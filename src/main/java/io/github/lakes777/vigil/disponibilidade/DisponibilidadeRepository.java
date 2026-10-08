package io.github.lakes777.vigil.disponibilidade;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Consultas de relatório em SQL puro (JdbcClient). Para somar e agrupar milhares de
 * verificações, uma consulta só no banco é bem mais rápida que carregar tudo no Java.
 */
@Repository
public class DisponibilidadeRepository {

	/** Os 3 períodos têm as mesmas contas, só muda o intervalo; o SQL é montado uma vez. */
	private static final String SQL_STATUS = """
			with numeros as (
			    select servico_id,
			        %s,
			        %s,
			        %s
			    from verificacao
			    where feita_em > :agora - interval '30 days' and feita_em <= :agora
			    group by servico_id
			)
			select s.id, s.nome, s.ativo, ultima.no_ar as ultima_no_ar, ultima.feita_em as ultima_em, numeros.*
			from servico s
			left join numeros on numeros.servico_id = s.id
			left join lateral (
			    select no_ar, feita_em from verificacao
			    where servico_id = s.id
			    order by feita_em desc, id desc
			    limit 1
			) ultima on true
			order by s.nome
			""".formatted(contas("24h", "24 hours"), contas("7d", "7 days"), contas("30d", "30 days"));

	/**
	 * Quedas pelo truque de "ilhas" (gaps and islands): numerando as verificações de cada serviço
	 * em ordem (geral) e dentro do seu tipo (no ar / fora), a diferença entre os dois números fica
	 * igual para todas as de uma mesma sequência seguida. Agrupar por ela junta cada queda.
	 * A numeração começa na última verificação no ar antes do período pedido (o "corte"):
	 * assim uma queda que já estava acontecendo aparece inteira, com o início e o motivo certos,
	 * sem numerar o histórico todo a cada visita da página (o índice por serviço e data acha o corte).
	 * O %s é o filtro de um serviço só (ou nada, para todos).
	 */
	private static final String SQL_QUEDAS = """
			with corte as (
			    select s.id as servico_id,
			        (select max(v.feita_em) from verificacao v
			         where v.servico_id = s.id and v.no_ar and v.feita_em <= :desde) as desde_no_ar
			    from servico s
			    where true %s
			),
			numeradas as (
			    select v.servico_id, v.id, v.feita_em, v.no_ar, v.erro,
			        row_number() over (partition by v.servico_id order by v.feita_em, v.id)
			          - row_number() over (partition by v.servico_id, v.no_ar order by v.feita_em, v.id) as ilha
			    from verificacao v
			    join corte c on c.servico_id = v.servico_id
			    where v.feita_em <= :agora and (c.desde_no_ar is null or v.feita_em >= c.desde_no_ar)
			),
			quedas as (
			    select servico_id, ilha, min(feita_em) as inicio, max(feita_em) as ultima_falha, count(*) as falhas,
			        (array_agg(erro order by feita_em, id))[1] as motivo
			    from numeradas
			    where not no_ar
			    group by servico_id, ilha
			    having max(feita_em) > :desde
			)
			select q.servico_id, s.nome, q.inicio, q.ultima_falha, q.falhas, q.motivo, s.ativo,
			    (select min(v.feita_em) from verificacao v
			     where v.servico_id = q.servico_id and v.no_ar
			       and v.feita_em > q.ultima_falha and v.feita_em <= :agora) as fim
			from quedas q
			join servico s on s.id = q.servico_id
			order by q.inicio desc, s.nome
			""";

	/**
	 * O tempo de resposta hora a hora, para o gráfico. Só as verificações no ar entram na média
	 * (como no resumo); as falhas são contadas à parte para marcar a hora no gráfico.
	 */
	private static final String SQL_TEMPOS = """
			select date_trunc('hour', feita_em) as hora,
			    count(*) as total, count(*) filter (where not no_ar) as falhas,
			    avg(tempo_ms) filter (where no_ar) as media,
			    percentile_cont(0.95) within group (order by tempo_ms) filter (where no_ar) as p95
			from verificacao
			where servico_id = :servico and feita_em >= :desde and feita_em <= :agora
			group by hora
			order by hora
			""";

	/** Agrupa pelo dia no horário de Brasília: uma queda às 23h não pode cair no "amanhã" do UTC. */
	private static final String SQL_DIAS = """
			select servico_id, (feita_em at time zone 'America/Sao_Paulo')::date as dia,
			    count(*) as total, count(*) filter (where no_ar) as no_ar
			from verificacao
			where feita_em >= :desde and feita_em <= :agora
			group by servico_id, dia
			order by servico_id, dia
			""";

	/**
	 * Os números de cada serviço entre dois instantes (o fim fica de fora). O left join traz
	 * também quem não teve nenhuma verificação no intervalo, com total 0. Sem p95: o resumo
	 * não usa, e ordenar a semana inteira de todos os serviços a cada prévia seria à toa.
	 */
	private static final String SQL_ENTRE = """
			select s.id, s.nome, s.ativo,
			    count(v.id) as total, count(v.id) filter (where v.no_ar) as no_ar,
			    avg(v.tempo_ms) filter (where v.no_ar) as media
			from servico s
			left join verificacao v on v.servico_id = s.id and v.feita_em >= :desde and v.feita_em < :ate
			group by s.id
			order by s.nome
			""";

	private final JdbcClient jdbc;

	public DisponibilidadeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<StatusServico> status(Instant agora) {
		return jdbc.sql(SQL_STATUS)
				.param("agora", comFuso(agora))
				.query((linha, n) -> new StatusServico(
						linha.getLong("id"),
						linha.getString("nome"),
						situacao(linha),
						instante(linha, "ultima_em"),
						periodo(linha, "24h"),
						periodo(linha, "7d"),
						periodo(linha, "30d")))
				.list();
	}

	public List<ServicoNoPeriodo> entre(Instant desde, Instant ate) {
		return jdbc.sql(SQL_ENTRE)
				.param("desde", comFuso(desde))
				.param("ate", comFuso(ate))
				.query((linha, n) -> new ServicoNoPeriodo(linha.getLong("id"), linha.getString("nome"),
						linha.getBoolean("ativo"), Periodo.de(linha.getLong("total"), linha.getLong("no_ar"),
								decimal(linha, "media"), null)))
				.list();
	}

	public List<Queda> quedas(Long servicoId, Instant desde, Instant agora) {
		return jdbc.sql(SQL_QUEDAS.formatted("and s.id = :servico"))
				.param("servico", servicoId)
				.param("desde", comFuso(desde))
				.param("agora", comFuso(agora))
				.query((linha, n) -> queda(linha, agora))
				.list();
	}

	/** As quedas de todos os serviços, da mais recente: o histórico de incidentes da página. */
	public List<Queda> quedas(Instant desde, Instant agora) {
		return jdbc.sql(SQL_QUEDAS.formatted(""))
				.param("desde", comFuso(desde))
				.param("agora", comFuso(agora))
				.query((linha, n) -> queda(linha, agora))
				.list();
	}

	public List<Hora> tempos(Long servicoId, Instant desde, Instant agora) {
		return jdbc.sql(SQL_TEMPOS)
				.param("servico", servicoId)
				.param("desde", comFuso(desde))
				.param("agora", comFuso(agora))
				.query((linha, n) -> new Hora(instante(linha, "hora"), linha.getLong("total"), linha.getLong("falhas"),
						Periodo.arredondar(decimal(linha, "media")), Periodo.arredondar(decimal(linha, "p95"))))
				.list();
	}

	private static Queda queda(ResultSet linha, Instant agora) throws SQLException {
		Instant inicio = instante(linha, "inicio");
		Instant fim = instante(linha, "fim");
		boolean emAndamento = fim == null && linha.getBoolean("ativo");
		if (fim == null && !emAndamento) {
			// Pausado enquanto estava fora: ninguém mais verifica, então a queda
			// termina na última falha vista (senão ficaria "em andamento" para sempre)
			fim = instante(linha, "ultima_falha");
		}
		long duracao = Duration.between(inicio, fim != null ? fim : agora).toSeconds();
		return new Queda(linha.getLong("servico_id"), linha.getString("nome"), inicio, fim, duracao, emAndamento,
				linha.getLong("falhas"), linha.getString("motivo"));
	}

	public List<Dia> dias(Instant desde, Instant agora) {
		return jdbc.sql(SQL_DIAS)
				.param("desde", comFuso(desde))
				.param("agora", comFuso(agora))
				.query((linha, n) -> {
					long total = linha.getLong("total");
					long noAr = linha.getLong("no_ar");
					return new Dia(linha.getLong("servico_id"), linha.getObject("dia", LocalDate.class),
							Periodo.porcentagem(noAr, total), total, total - noAr);
				})
				.list();
	}

	/** count/avg/percentil de um período, com nomes como total_24h, no_ar_24h, media_24h, p95_24h. */
	private static String contas(String sufixo, String intervalo) {
		String noPeriodo = "feita_em > :agora - interval '" + intervalo + "'";
		return String.join(",\n        ",
				"count(*) filter (where " + noPeriodo + ") as total_" + sufixo,
				"count(*) filter (where no_ar and " + noPeriodo + ") as no_ar_" + sufixo,
				"avg(tempo_ms) filter (where no_ar and " + noPeriodo + ") as media_" + sufixo,
				"percentile_cont(0.95) within group (order by tempo_ms) filter (where no_ar and " + noPeriodo
						+ ") as p95_" + sufixo);
	}

	private static Situacao situacao(ResultSet linha) throws SQLException {
		if (!linha.getBoolean("ativo")) {
			return Situacao.PAUSADO;
		}
		boolean noAr = linha.getBoolean("ultima_no_ar");
		if (linha.wasNull()) {
			return Situacao.SEM_DADOS;
		}
		return noAr ? Situacao.NO_AR : Situacao.FORA;
	}

	private static Periodo periodo(ResultSet linha, String sufixo) throws SQLException {
		return Periodo.de(linha.getLong("total_" + sufixo), linha.getLong("no_ar_" + sufixo),
				decimal(linha, "media_" + sufixo), decimal(linha, "p95_" + sufixo));
	}

	private static Double decimal(ResultSet linha, String coluna) throws SQLException {
		Number valor = (Number) linha.getObject(coluna);
		return valor == null ? null : valor.doubleValue();
	}

	private static Instant instante(ResultSet linha, String coluna) throws SQLException {
		OffsetDateTime valor = linha.getObject(coluna, OffsetDateTime.class);
		return valor == null ? null : valor.toInstant();
	}

	/** O driver do Postgres não aceita Instant direto; OffsetDateTime vira timestamptz. */
	private static OffsetDateTime comFuso(Instant instante) {
		return instante.atOffset(ZoneOffset.UTC);
	}

}
