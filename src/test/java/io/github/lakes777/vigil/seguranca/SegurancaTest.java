package io.github.lakes777.vigil.seguranca;

import static io.github.lakes777.vigil.Admin.comChave;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.lakes777.vigil.TestcontainersConfiguration;
import io.github.lakes777.vigil.servico.Servico;
import io.github.lakes777.vigil.servico.ServicoRepository;

/** Quem pode o quê: ler é público, alterar pede a chave. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SegurancaTest {

	private static final String JSON = "{\"nome\":\"Encore\",\"url\":\"https://exemplo.com\"}";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicoRepository servicos;

	@Value("${vigil.admin.chave}")
	private String chave;

	private long id;

	@BeforeEach
	void preparar() {
		servicos.deleteAll();
		id = servicos.save(new Servico("Hanami", "https://exemplo.com", 300, true)).getId();
	}

	@Test
	void lerEPublico() throws Exception {
		mvc.perform(get("/api/servicos")).andExpect(status().isOk());
		mvc.perform(get("/api/servicos/" + id)).andExpect(status().isOk());
		mvc.perform(get("/api/status")).andExpect(status().isOk());
		mvc.perform(get("/api/servicos/" + id + "/verificacoes")).andExpect(status().isOk());
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
		// A página de status
		mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
		mvc.perform(get("/index.html")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Status dos projetos")));
		mvc.perform(get("/api/status/dias")).andExpect(status().isOk());
	}

	@Test
	void actuatorSoMostraASaude() throws Exception {
		// Estes entregariam variáveis de ambiente (com a chave) e a memória da API
		mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
		mvc.perform(get("/actuator/heapdump")).andExpect(status().isNotFound());
	}

	@Test
	void bearerEmMinusculasTambemVale() throws Exception {
		mvc.perform(delete("/api/servicos/" + id).header(HttpHeaders.AUTHORIZATION, "bearer " + chave))
				.andExpect(status().isNoContent());
	}

	@Test
	void chaveErradaDa401AteNoGet() throws Exception {
		// Melhor avisar que a chave está errada do que ignorar em silêncio
		mvc.perform(get("/api/servicos").with(comChave("errada")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Chave inválida"));
	}

	@Test
	void swaggerSoPoeCadeadoNasRotasQueAlteram() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/servicos'].get.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/servicos'].post.security[0].chave").exists())
				.andExpect(jsonPath("$.paths['/api/servicos/{id}/verificar'].post.security[0].chave").exists());
	}

	@Test
	void alterarSemChaveDa401() throws Exception {
		semChave(post("/api/servicos").contentType(MediaType.APPLICATION_JSON).content(JSON));
		semChave(put("/api/servicos/" + id).contentType(MediaType.APPLICATION_JSON).content(JSON));
		semChave(delete("/api/servicos/" + id));
		semChave(post("/api/servicos/" + id + "/verificar"));
	}

	private void semChave(MockHttpServletRequestBuilder pedido) throws Exception {
		mvc.perform(pedido)
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Chave de admin necessária"));
	}

	@Test
	void nadaMudaSemAChave() throws Exception {
		mvc.perform(delete("/api/servicos/" + id)).andExpect(status().isUnauthorized());

		mvc.perform(get("/api/servicos/" + id)).andExpect(status().isOk());
	}

	@Test
	void chaveErradaDa401() throws Exception {
		mvc.perform(delete("/api/servicos/" + id).with(comChave(chave + "x")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Chave inválida"));
		mvc.perform(delete("/api/servicos/" + id).with(comChave(chave.substring(1))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void outroTipoDeAutorizacaoDa401() throws Exception {
		mvc.perform(delete("/api/servicos/" + id).header(HttpHeaders.AUTHORIZATION, "Basic " + chave))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Chave inválida"));
	}

	@Test
	void comAChaveAlteraNormalmente() throws Exception {
		mvc.perform(post("/api/servicos").with(comChave(chave)).contentType(MediaType.APPLICATION_JSON).content(JSON))
				.andExpect(status().isCreated());
		mvc.perform(delete("/api/servicos/" + id).with(comChave(chave))).andExpect(status().isNoContent());
	}

	@Test
	void rotaInexistenteComChaveDa404() throws Exception {
		mvc.perform(post("/api/nada").with(comChave(chave))).andExpect(status().isNotFound());
	}

	@Test
	void naoSobeSemChaveOuComChaveCurta() {
		assertThatThrownBy(() -> new SegurancaConfig(""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("vigil.admin.chave");
		assertThatThrownBy(() -> new SegurancaConfig("a".repeat(SegurancaConfig.TAMANHO_MINIMO - 1)))
				.isInstanceOf(IllegalStateException.class);
		new SegurancaConfig("a".repeat(SegurancaConfig.TAMANHO_MINIMO));
	}

}
