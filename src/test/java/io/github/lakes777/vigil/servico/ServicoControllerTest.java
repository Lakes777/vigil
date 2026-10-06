package io.github.lakes777.vigil.servico;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.github.lakes777.vigil.TestcontainersConfiguration;

/** Testes de integração: a aplicação inteira com um Postgres de verdade (Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ServicoControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicoRepository repositorio;

	@BeforeEach
	void limparBanco() {
		repositorio.deleteAll();
	}

	private ResultActions criar(String json) throws Exception {
		return mvc.perform(post("/api/servicos").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private long criarERetornarId(String nome) throws Exception {
		String corpo = criar("{\"nome\":\"" + nome + "\",\"url\":\"https://exemplo.com\"}")
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(corpo, "$.id")).longValue();
	}

	@Test
	void criaComPadroesELocation() throws Exception {
		criar("{\"nome\":\"Encore\",\"url\":\"https://encore.exemplo.com\"}")
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/servicos/" + repositorio.findAll().getFirst().getId())))
				.andExpect(jsonPath("$.nome").value("Encore"))
				.andExpect(jsonPath("$.intervaloSegundos").value(300))
				.andExpect(jsonPath("$.ativo").value(true))
				.andExpect(jsonPath("$.criadoEm").isNotEmpty());
	}

	@Test
	void listaEmOrdemAlfabetica() throws Exception {
		criarERetornarId("Spendwise");
		criarERetornarId("Coursebook");
		criarERetornarId("Hanami");

		mvc.perform(get("/api/servicos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].nome").value("Coursebook"))
				.andExpect(jsonPath("$[2].nome").value("Spendwise"));
	}

	@Test
	void buscaEditaERemove() throws Exception {
		long id = criarERetornarId("Hanami");

		mvc.perform(put("/api/servicos/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Hanami\",\"url\":\"https://novo.exemplo.com\",\"intervaloSegundos\":600,\"ativo\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("https://novo.exemplo.com"));

		mvc.perform(get("/api/servicos/" + id))
				.andExpect(jsonPath("$.intervaloSegundos").value(600))
				.andExpect(jsonPath("$.ativo").value(false));

		mvc.perform(delete("/api/servicos/" + id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/servicos/" + id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Serviço não encontrado"));
	}

	@Test
	void editarComONomeDeleMesmoComOutraCaixaFunciona() throws Exception {
		long id = criarERetornarId("Hanami");

		mvc.perform(put("/api/servicos/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"HANAMI\",\"url\":\"https://exemplo.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("HANAMI"));
	}

	@Test
	void putSemOsOpcionaisNaoReligaServicoPausado() throws Exception {
		String corpo = criar("{\"nome\":\"Hanami\",\"url\":\"https://a.com\",\"intervaloSegundos\":600,\"ativo\":false}")
				.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(corpo, "$.id")).longValue();

		mvc.perform(put("/api/servicos/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Hanami\",\"url\":\"https://b.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("https://b.com"))
				.andExpect(jsonPath("$.intervaloSegundos").value(600))
				.andExpect(jsonPath("$.ativo").value(false));
	}

	@Test
	void putComONomeDeOutroServicoDa409() throws Exception {
		criarERetornarId("Encore");
		long id = criarERetornarId("Hanami");

		mvc.perform(put("/api/servicos/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"ENCORE\",\"url\":\"https://exemplo.com\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Nome em uso"));
	}

	@Test
	void getEPutDeIdInexistenteDao404() throws Exception {
		mvc.perform(get("/api/servicos/999999")).andExpect(status().isNotFound());
		mvc.perform(put("/api/servicos/999999").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"X\",\"url\":\"https://x.com\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void validaOsLimitesDeTamanhoEIntervalo() throws Exception {
		criar("{\"nome\":\"" + "a".repeat(81) + "\",\"url\":\"https://x.com\",\"intervaloSegundos\":86401}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.nome").value("use no máximo 80 caracteres"))
				.andExpect(jsonPath("$.campos.intervaloSegundos").value("o intervalo máximo é de 1 dia (86400 segundos)"));
	}

	@Test
	void recusaCampoComTipoErrado() throws Exception {
		criar("{\"nome\":\"X\",\"url\":\"https://x.com\",\"ativo\":\"talvez\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("JSON inválido"));
	}

	@Test
	void recusaNomeRepetidoSemDiferenciarMaiusculas() throws Exception {
		criarERetornarId("Encore");

		criar("{\"nome\":\"encore\",\"url\":\"https://outro.com\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Já existe um serviço chamado \"encore\"."));
	}

	@Test
	void indiceDoBancoBarraNomeRepetidoMesmoSemAChecagem() {
		repositorio.saveAndFlush(new Servico("Tidy", "https://a.com", 300, true));

		assertThatThrownBy(() -> repositorio.saveAndFlush(new Servico("TIDY", "https://b.com", 300, true)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void validaOsCampos() throws Exception {
		criar("{\"nome\":\"  \",\"url\":\"ftp://exemplo.com\",\"intervaloSegundos\":10}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Dados inválidos"))
				.andExpect(jsonPath("$.campos.nome").value("informe o nome"))
				.andExpect(jsonPath("$.campos.url").value("use uma URL que comece com http:// ou https://"))
				.andExpect(jsonPath("$.campos.intervaloSegundos").value("o intervalo mínimo é de 60 segundos"));
	}

	@Test
	void recusaJsonQuebrado() throws Exception {
		criar("{\"nome\":").andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("JSON inválido"));
	}

	@Test
	void recusaIdQueNaoENumero() throws Exception {
		mvc.perform(get("/api/servicos/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Parâmetro inválido"))
				.andExpect(jsonPath("$.detail").value("O valor \"abc\" não serve para id."));
	}

	@Test
	void removerInexistenteDa404() throws Exception {
		mvc.perform(delete("/api/servicos/999999")).andExpect(status().isNotFound());
	}

	@Test
	void documentacaoOpenApiNoAr() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Vigil"))
				.andExpect(jsonPath("$.paths['/api/servicos']").exists());
	}

}
