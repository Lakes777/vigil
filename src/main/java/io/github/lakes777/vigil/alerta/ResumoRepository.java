package io.github.lakes777.vigil.alerta;

import java.time.LocalDate;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** As semanas cujo resumo já foi mandado (tabela resumo_semanal), pela segunda-feira de cada uma. */
@Repository
public class ResumoRepository {

	private final JdbcClient jdbc;

	public ResumoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public boolean enviado(LocalDate semana) {
		return jdbc.sql("select exists (select 1 from resumo_semanal where semana = :semana)")
				.param("semana", semana)
				.query(Boolean.class)
				.single();
	}

	/**
	 * Marca a semana antes de mandar. Devolve false se ela já estava marcada: com duas
	 * tentativas ao mesmo tempo, a chave primária deixa só uma passar.
	 */
	public boolean marcar(LocalDate semana) {
		return jdbc.sql("insert into resumo_semanal (semana) values (:semana) on conflict do nothing")
				.param("semana", semana)
				.update() == 1;
	}

	/** Desfaz a marca de um resumo que não chegou, para a próxima tentativa mandar de novo. */
	public void desmarcar(LocalDate semana) {
		jdbc.sql("delete from resumo_semanal where semana = :semana")
				.param("semana", semana)
				.update();
	}

}
