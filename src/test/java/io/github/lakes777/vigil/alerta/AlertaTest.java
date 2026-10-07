package io.github.lakes777.vigil.alerta;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import io.github.lakes777.vigil.TestcontainersConfiguration;
import io.github.lakes777.vigil.servico.Servico;
import io.github.lakes777.vigil.servico.ServicoEntrada;
import io.github.lakes777.vigil.servico.ServicoRepository;
import io.github.lakes777.vigil.servico.ServicoService;
import io.github.lakes777.vigil.verificacao.ResultadoSonda;
import io.github.lakes777.vigil.verificacao.Verificacao;
import io.github.lakes777.vigil.verificacao.VerificacaoRepository;
import io.github.lakes777.vigil.verificacao.Verificador;

/**
 * De ponta a ponta: um site falso que cai e volta, e um Telegram falso que recebe os avisos.
 * Os dois são WireMock; o Postgres é de verdade (Testcontainers).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AlertaTest {

	private static final String ENVIO = "/bot123:abc/sendMessage";

	@RegisterExtension
	static WireMockExtension site = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@RegisterExtension
	static WireMockExtension telegram = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@DynamicPropertySource
	static void telegramFalso(DynamicPropertyRegistry propriedades) {
		propriedades.add("vigil.alerta.telegram.url", telegram::baseUrl);
		propriedades.add("vigil.alerta.telegram.token", () -> "123:abc");
		propriedades.add("vigil.alerta.telegram.chat", () -> "42");
	}

	@Autowired
	private Verificador verificador;

	@Autowired
	private ServicoRepository servicos;

	@Autowired
	private AlertaRepository alertas;

	@Autowired
	private VerificacaoRepository verificacoes;

	@Autowired
	private ServicoService cadastro;

	private Long id;

	@BeforeEach
	void preparar() {
		servicos.deleteAll();
		id = servicos.save(new Servico("Hanami", site.baseUrl() + "/", 300, true)).getId();
		telegram.stubFor(post(ENVIO).willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));
	}

	private void siteNoAr() {
		site.stubFor(get("/").willReturn(aResponse().withStatus(200)));
	}

	private void siteFora() {
		site.stubFor(get("/").willReturn(aResponse().withStatus(503)));
	}

	private int avisos() {
		return telegram.findAll(postRequestedFor(urlEqualTo(ENVIO))).size();
	}

	@Test
	void umaFalhaSoNaoAvisa() {
		siteFora();
		verificador.verificarAgora(id);

		assertThat(avisos()).isZero();
		assertThat(alertas.foraDesde(id)).isEmpty();
	}

	@Test
	void duasFalhasSeguidasAvisamUmaVezSo() {
		siteFora();
		verificador.verificarAgora(id);
		verificador.verificarAgora(id);
		verificador.verificarAgora(id);

		assertThat(avisos()).isEqualTo(1);
		telegram.verify(postRequestedFor(urlEqualTo(ENVIO))
				.withFormParam("chat_id", equalTo("42"))
				.withFormParam("text", containing("Vigil: Hanami caiu (respondeu com o código 503). Fora desde ")));
		assertThat(alertas.foraDesde(id)).isPresent();
	}

	@Test
	void avisaQuandoVolta() {
		siteFora();
		verificador.verificarAgora(id);
		verificador.verificarAgora(id);
		siteNoAr();
		verificador.verificarAgora(id);
		verificador.verificarAgora(id);

		assertThat(avisos()).isEqualTo(2);
		telegram.verify(1, postRequestedFor(urlEqualTo(ENVIO))
				.withFormParam("text", containing("Vigil: Hanami voltou. Ficou fora por menos de 1 min")));
		assertThat(alertas.foraDesde(id)).isEmpty();
	}

	@Test
	void falhasIntercaladasNaoAvisam() {
		// Como o Render: dorme, a 1ª visita falha, a seguinte já responde
		siteFora();
		verificador.verificarAgora(id);
		siteNoAr();
		verificador.verificarAgora(id);
		siteFora();
		verificador.verificarAgora(id);

		assertThat(avisos()).isZero();
	}

	@Test
	void telegramForaTentaDeNovoNaProximaVerificacao() {
		telegram.stubFor(post(ENVIO).willReturn(aResponse().withStatus(502)));
		siteFora();
		verificador.verificarAgora(id);
		verificador.verificarAgora(id);

		assertThat(avisos()).isEqualTo(1);
		assertThat(alertas.foraDesde(id)).as("aviso não entregue não fica aberto").isEmpty();

		telegram.stubFor(post(ENVIO).willReturn(aResponse().withStatus(200)));
		verificador.verificarAgora(id);

		assertThat(avisos()).isEqualTo(2);
		assertThat(alertas.foraDesde(id)).isPresent();
	}

	@Test
	void falhaDeDiasAtrasNaoSeSomaComADeAgora() {
		// A última verificação antes de a API ficar 3 dias desligada falhou
		Servico servico = servicos.findById(id).orElseThrow();
		verificacoes.save(new Verificacao(servico, Instant.now().minus(Duration.ofDays(3)),
				new ResultadoSonda(false, null, 10_000, "tempo esgotado (10 s)")));
		siteFora();

		verificador.verificarAgora(id);

		assertThat(avisos()).isZero();
		verificador.verificarAgora(id);
		// Agora sim duas seguidas, e o "fora desde" é o de hoje, não o de 3 dias atrás
		assertThat(avisos()).isEqualTo(1);
		assertThat(alertas.foraDesde(id)).hasValueSatisfying(
				desde -> assertThat(desde).isAfter(Instant.now().minus(Duration.ofMinutes(1))));
	}

	@Test
	void foraDesdeEOComecoDaQuedaMesmoComOTelegramFalhandoAntes() {
		telegram.stubFor(post(ENVIO).willReturn(aResponse().withStatus(502)));
		siteFora();
		verificador.verificarAgora(id);
		Instant primeira = verificacoes.findAll().getFirst().getFeitaEm();
		verificador.verificarAgora(id);
		verificador.verificarAgora(id);
		telegram.stubFor(post(ENVIO).willReturn(aResponse().withStatus(200)));

		verificador.verificarAgora(id);

		assertThat(alertas.foraDesde(id)).contains(primeira.truncatedTo(ChronoUnit.MICROS));
	}

	@Test
	void voltouComOTelegramForaTentaDeNovo() {
		siteFora();
		verificador.verificarAgora(id);
		verificador.verificarAgora(id);
		telegram.stubFor(post(ENVIO).willReturn(aResponse().withStatus(502)));
		siteNoAr();

		verificador.verificarAgora(id);

		assertThat(alertas.foraDesde(id)).as("o voltou não chegou: o aviso continua aberto").isPresent();
		telegram.stubFor(post(ENVIO).willReturn(aResponse().withStatus(200)));
		verificador.verificarAgora(id);
		assertThat(alertas.foraDesde(id)).isEmpty();
		// Duas tentativas de "voltou": a que recebeu 502 e a que chegou
		telegram.verify(2, postRequestedFor(urlEqualTo(ENVIO)).withFormParam("text", containing("voltou"))
				.withHeader("Content-Type", containing("x-www-form-urlencoded")));
	}

	@Test
	void soUmaVerificacaoConsegueAbrirOAviso() {
		Instant agora = Instant.now();

		assertThat(alertas.abrir(id, agora)).isTrue();
		assertThat(alertas.abrir(id, agora)).isFalse();
		assertThat(alertas.fechar(id)).isPresent();
		assertThat(alertas.fechar(id)).isEmpty();
	}

	@Test
	void pausarOuTrocarAUrlEsqueceOAvisoSemMandarVoltou() {
		siteFora();
		verificador.verificarAgora(id);
		verificador.verificarAgora(id);
		assertThat(alertas.foraDesde(id)).isPresent();

		cadastro.atualizar(id, new ServicoEntrada("Hanami", site.baseUrl() + "/", null, false));

		assertThat(alertas.foraDesde(id)).isEmpty();
		alertas.abrir(id, Instant.now());
		cadastro.atualizar(id, new ServicoEntrada("Hanami", site.baseUrl() + "/novo", null, null));
		assertThat(alertas.foraDesde(id)).isEmpty();
		assertThat(avisos()).isEqualTo(1);
	}

	@Test
	void editarSoONomeMantemOAviso() {
		siteFora();
		verificador.verificarAgora(id);
		verificador.verificarAgora(id);

		cadastro.atualizar(id, new ServicoEntrada("Hanami (Render)", site.baseUrl() + "/", null, null));

		assertThat(alertas.foraDesde(id)).isPresent();
	}

	@Test
	void reinicioNaoRepeteOAviso() {
		siteFora();
		verificador.verificarAgora(id);
		verificador.verificarAgora(id);
		// O estado fica no banco: um Alertas novo (como depois de reiniciar a API) sabe que já avisou
		Alertas depoisDoReinicio = new Alertas(alertas, new Telegram(telegram.baseUrl(), "123:abc", "42"), 2);

		depoisDoReinicio.avaliar(id, "Hanami", 300, false, "respondeu com o código 503");

		assertThat(avisos()).isEqualTo(1);
	}

	@Test
	void servicoApagadoNaoQuebraOAviso() {
		Alertas alertasDeTeste = new Alertas(alertas, new Telegram(telegram.baseUrl(), "123:abc", "42"), 2);

		alertasDeTeste.avaliar(999_999L, "Sumiu", 300, true, null);
		alertasDeTeste.avaliar(999_999L, "Sumiu", 300, false, "erro");

		assertThat(avisos()).isZero();
	}

}
