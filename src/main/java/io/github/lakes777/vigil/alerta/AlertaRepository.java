package io.github.lakes777.vigil.alerta;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * O estado dos avisos (coluna servico.alerta_fora_desde) em SQL puro.
 * Abrir e fechar são "update ... where": se duas verificações do mesmo serviço
 * terminarem juntas (a manual e a do agendador), só uma consegue mudar a linha,
 * então só uma manda o aviso.
 */
@Repository
public class AlertaRepository {

	private final JdbcClient jdbc;

	public AlertaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * As falhas depois da última verificação no ar, da mais recente para a mais antiga
	 * (no máximo 200: para decidir o aviso não é preciso mais que isso).
	 */
	List<Instant> falhasDesdeOUltimoOk(Long servicoId) {
		return jdbc.sql("""
				select feita_em from verificacao
				where servico_id = :servico and not no_ar
				  and feita_em > coalesce(
				      (select max(feita_em) from verificacao where servico_id = :servico and no_ar),
				      '-infinity'::timestamptz)
				order by feita_em desc, id desc
				limit 200
				""")
				.param("servico", servicoId)
				.query((linha, n) -> instante(linha, "feita_em"))
				.list();
	}

	/** Marca "fora desde". Devolve false se o aviso já estava aberto (outra verificação chegou antes). */
	@Transactional
	public boolean abrir(Long servicoId, Instant desde) {
		return jdbc.sql("update servico set alerta_fora_desde = :desde where id = :servico and alerta_fora_desde is null")
				.param("servico", servicoId)
				.param("desde", comFuso(desde))
				.update() == 1;
	}

	/**
	 * Fecha o aviso e devolve desde quando estava fora (vazio se não havia aviso aberto).
	 * O "for update" trava a linha: uma segunda verificação ao mesmo tempo espera, relê
	 * e já encontra o aviso fechado.
	 */
	@Transactional
	public Optional<Instant> fechar(Long servicoId) {
		return jdbc.sql("""
				with aberto as (
				    select id, alerta_fora_desde as desde from servico
				    where id = :servico and alerta_fora_desde is not null
				    for update
				)
				update servico s set alerta_fora_desde = null
				from aberto
				where s.id = aberto.id
				returning aberto.desde
				""")
				.param("servico", servicoId)
				.query((linha, n) -> instante(linha, "desde"))
				.optional();
	}

	/** Desfaz um abrir() cujo aviso não chegou, para a próxima verificação tentar de novo. */
	@Transactional
	public void desfazerAbertura(Long servicoId, Instant desde) {
		jdbc.sql("update servico set alerta_fora_desde = null where id = :servico and alerta_fora_desde = :desde")
				.param("servico", servicoId)
				.param("desde", comFuso(desde))
				.update();
	}

	/** Desfaz um fechar() cujo aviso não chegou. */
	@Transactional
	public void desfazerFechamento(Long servicoId, Instant desde) {
		jdbc.sql("update servico set alerta_fora_desde = :desde where id = :servico and alerta_fora_desde is null")
				.param("servico", servicoId)
				.param("desde", comFuso(desde))
				.update();
	}

	/** Para os testes e para quem quiser saber se há aviso aberto. */
	public Optional<Instant> foraDesde(Long servicoId) {
		return jdbc.sql("select alerta_fora_desde from servico where id = :servico")
				.param("servico", servicoId)
				.query((linha, n) -> Optional.ofNullable(instante(linha, "alerta_fora_desde")))
				.optional()
				.flatMap(valor -> valor);
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
