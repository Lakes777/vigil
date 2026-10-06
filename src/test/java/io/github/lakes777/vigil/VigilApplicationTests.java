package io.github.lakes777.vigil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class VigilApplicationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void saudeRespondeUp() throws Exception {
		mvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void flywayAplicaTodasAsMigracoes() {
		// Não depende do conteúdo da tabela: outros testes dividem o mesmo banco
		Integer falhas = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where not success", Integer.class);
		Integer tabelas = jdbc.queryForObject(
				"select count(*) from information_schema.tables where table_name = 'servico'", Integer.class);
		assertThat(falhas).isZero();
		assertThat(tabelas).isEqualTo(1);
	}

}
