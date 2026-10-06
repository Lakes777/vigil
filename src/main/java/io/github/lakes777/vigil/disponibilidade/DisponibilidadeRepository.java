package io.github.lakes777.vigil.disponibilidade;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
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
	 * Quedas pelo truque de "ilhas" (gaps and islands): numerando as verificações em ordem
	 * (geral) e dentro do seu tipo (no ar / fora), a diferença entre os dois números fica
	 * igual para todas as de uma mesma sequência seguida. Agrupar por ela junta cada queda.
	 * A numeração olha o histórico todo (no máximo 90 dias): assim uma queda que começou
	 * antes do período pedido aparece inteira, com o início e o motivo certos.
	 */
	private static final String SQL_QUEDAS = """
			with numeradas as (
			    select id, feita_em, no_ar, erro,
			        row_number() over (order by feita_em, id)
			          - row_number() over (partition by no_ar order by feita_em, id) as ilha
			    from verificacao
			    where servico_id = :servico and feita_em <= :agora
			),
			quedas as (
			    select ilha, min(feita_em) as inicio, max(feita_em) as ultima_falha, count(*) as falhas,
			        (array_agg(erro order by feita_em, id))[1] as motivo
			    from numeradas
			    where not no_ar
			    group by ilha
			    having max(feita_em) > :desde
			)
			select q.inicio, q.ultima_falha, q.falhas, q.motivo, s.ativo,
			    (select min(v.feita_em) from verificacao v
			     where v.servico_id = :servico and v.no_ar
			       and v.feita_em > q.ultima_falha and v.feita_em <= :agora) as fim
			from quedas q
			join servico s on s.id = :servico
			order by q.inicio desc
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

	public List<Queda> quedas(Long servicoId, Instant desde, Instant agora) {
		return jdbc.sql(SQL_QUEDAS)
				.param("servico", servicoId)
				.param("desde", comFuso(desde))
				.param("agora", comFuso(agora))
				.query((linha, n) -> {
					Instant inicio = instante(linha, "inicio");
					Instant fim = instante(linha, "fim");
					boolean emAndamento = fim == null && linha.getBoolean("ativo");
					if (fim == null && !emAndamento) {
						// Pausado enquanto estava fora: ninguém mais verifica, então a queda
						// termina na última falha vista (senão ficaria "em andamento" para sempre)
						fim = instante(linha, "ultima_falha");
					}
					long duracao = Duration.between(inicio, fim != null ? fim : agora).toSeconds();
					return new Queda(inicio, fim, duracao, emAndamento, linha.getLong("falhas"),
							linha.getString("motivo"));
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
