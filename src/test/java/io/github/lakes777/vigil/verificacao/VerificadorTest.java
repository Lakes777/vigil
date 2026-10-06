package io.github.lakes777.vigil.verificacao;

import static io.github.lakes777.vigil.Admin.comChave;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import io.github.lakes777.vigil.TestcontainersConfiguration;
import io.github.lakes777.vigil.servico.Servico;
import io.github.lakes777.vigil.servico.ServicoRepository;

/**
 * Verificações de ponta a ponta: Postgres de verdade (Testcontainers) e sites falsos (WireMock).
 * WireMock e MockMvc têm um get() cada; o do MockMvc vai com o nome da classe.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class VerificadorTest {

	@RegisterExtension
	static WireMockExtension site = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@Autowired
	private Verificador verificador;

	@Autowired
	private ServicoRepository servicos;

	@Autowired
	private VerificacaoRepository verificacoes;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Value("${vigil.admin.chave}")
	private String chave;

	@BeforeEach
	void preparar() {
		servicos.deleteAll();
		site.stubFor(get("/ok").willReturn(aResponse().withStatus(200)));
		site.stubFor(get("/quebrado").willReturn(aResponse().withStatus(500)));
		site.stubFor(get("/lento").willReturn(aResponse().withStatus(200).withFixedDelay(500)));
	}

	private Servico cadastrar(String nome, String caminho, boolean ativo) {
		return servicos.save(new Servico(nome, site.baseUrl() + caminho, 300, ativo));
	}

	private void adiarUmaHora(Servico servico) {
		jdbc.update("update servico set proxima_verificacao = now() + interval '1 hour' where id = ?", servico.getId());
	}

	@Test
	void verificaSoOsAtivosCujaHoraChegou() {
		Servico pendente = cadastrar("Pendente", "/ok", true);
		Servico depois = cadastrar("Depois", "/ok", true);
		Servico pausado = cadastrar("Pausado", "/ok", false);
		adiarUmaHora(depois);

		assertThat(verificador.verificarPendentes()).isEqualTo(1);

		assertThat(verificador.historico(pendente.getId(), 50)).hasSize(1);
		assertThat(verificador.historico(depois.getId(), 50)).isEmpty();
		assertThat(verificador.historico(pausado.getId(), 50)).isEmpty();
	}

	@Test
	void gravaOResultadoEAgendaAProxima() {
		Servico quebrado = cadastrar("Quebrado", "/quebrado", true);

		verificador.verificarPendentes();

		VerificacaoResposta feita = verificador.historico(quebrado.getId(), 1).getFirst();
		assertThat(feita.noAr()).isFalse();
		assertThat(feita.codigoHttp()).isEqualTo(500);
		assertThat(servicos.findById(quebrado.getId()).orElseThrow().getProximaVerificacao())
				.isCloseTo(feita.feitaEm().plusSeconds(300), within(1, ChronoUnit.SECONDS));
		// Já verificado: na próxima rodada não está mais pendente
		assertThat(verificador.verificarPendentes()).isZero();
	}

	@Test
	void edicaoFeitaDuranteAVerificacaoNaoEhSobrescrita() {
		Servico servico = cadastrar("Coursebook", "/ok", true);
		Instant lidaAntes = servicos.findById(servico.getId()).orElseThrow().getProximaVerificacao();
		// Enquanto a Sonda acessava o site, alguém editou o serviço (mudou a próxima verificação)
		jdbc.update("update servico set proxima_verificacao = now() - interval '1 minute' where id = ?", servico.getId());
		Instant depoisDaEdicao = servicos.findById(servico.getId()).orElseThrow().getProximaVerificacao();

		int mudou = servicos.agendarDepoisDaVerificacao(servico.getId(), Instant.now(), lidaAntes);

		assertThat(mudou).isZero();
		assertThat(servicos.findById(servico.getId()).orElseThrow().getProximaVerificacao()).isEqualTo(depoisDaEdicao);
	}

	@Test
	void aProximaUsaOIntervaloGravadoNoBanco() {
		Servico servico = servicos.save(new Servico("Tidy", site.baseUrl() + "/ok", 600, true));

		verificador.verificarAgora(servico.getId());

		VerificacaoResposta feita = verificador.historico(servico.getId(), 1).getFirst();
		assertThat(servicos.findById(servico.getId()).orElseThrow().getProximaVerificacao())
				.isCloseTo(feita.feitaEm().plusSeconds(600), within(1, ChronoUnit.SECONDS));
	}

	@Test
	void verificaEmParalelo() {
		for (int i = 1; i <= 5; i++) {
			cadastrar("Lento " + i, "/lento", true);
		}

		long inicio = System.nanoTime();
		assertThat(verificador.verificarPendentes()).isEqualTo(5);
		Duration levou = Duration.ofNanos(System.nanoTime() - inicio);

		// Um por vez levaria 5 x 500 ms = 2,5 s (folga para o CI, que é mais lento)
		assertThat(levou).isLessThan(Duration.ofMillis(2400));
		assertThat(verificacoes.count()).isEqualTo(5);
	}

	@Test
	void historicoVemDoMaisRecenteERespeitaOLimite() {
		Servico servico = cadastrar("Encore", "/ok", true);
		for (int i = 0; i < 3; i++) {
			verificador.verificarAgora(servico.getId());
		}

		var historico = verificador.historico(servico.getId(), 2);

		assertThat(historico).hasSize(2);
		assertThat(historico.get(0).id()).isGreaterThan(historico.get(1).id());
		assertThat(verificador.historico(servico.getId(), 0)).hasSize(1);
	}

	@Test
	void verificarAgoraPelaApi() throws Exception {
		Servico servico = cadastrar("Hanami", "/ok", false);

		mvc.perform(MockMvcRequestBuilders.post("/api/servicos/" + servico.getId() + "/verificar").with(comChave(chave)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.noAr").value(true))
				.andExpect(jsonPath("$.codigoHttp").value(200));

		mvc.perform(MockMvcRequestBuilders.get("/api/servicos/" + servico.getId() + "/verificacoes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void verificarAgoraTemLimitePorServico() throws Exception {
		Servico hanami = cadastrar("Hanami", "/ok", true);
		Servico encore = cadastrar("Encore", "/ok", true);
		String rota = "/api/servicos/" + hanami.getId() + "/verificar";

		mvc.perform(MockMvcRequestBuilders.post(rota).with(comChave(chave))).andExpect(status().isOk());
		mvc.perform(MockMvcRequestBuilders.post(rota).with(comChave(chave)))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", matchesPattern("([1-9]|10)")))
				.andExpect(jsonPath("$.title").value("Muitas verificações"));
		mvc.perform(MockMvcRequestBuilders.post("/api/servicos/" + encore.getId() + "/verificar").with(comChave(chave)))
				.andExpect(status().isOk());

		assertThat(verificacoes.count()).isEqualTo(2);
	}

	@Test
	void servicoInexistenteDa404() throws Exception {
		mvc.perform(MockMvcRequestBuilders.post("/api/servicos/999999/verificar").with(comChave(chave))).andExpect(status().isNotFound());
		mvc.perform(MockMvcRequestBuilders.get("/api/servicos/999999/verificacoes")).andExpect(status().isNotFound());
	}

	@Test
	void apagarOServicoApagaOHistorico() throws Exception {
		Servico servico = cadastrar("Tidy", "/ok", true);
		verificador.verificarAgora(servico.getId());

		mvc.perform(MockMvcRequestBuilders.delete("/api/servicos/" + servico.getId()).with(comChave(chave))).andExpect(status().isNoContent());

		assertThat(verificacoes.count()).isZero();
	}

}
