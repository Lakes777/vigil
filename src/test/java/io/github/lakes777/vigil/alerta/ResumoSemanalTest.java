package io.github.lakes777.vigil.alerta;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.github.lakes777.vigil.Admin.comChave;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import io.github.lakes777.vigil.TestcontainersConfiguration;
import io.github.lakes777.vigil.servico.Servico;
import io.github.lakes777.vigil.servico.ServicoRepository;
import io.github.lakes777.vigil.verificacao.ResultadoSonda;
import io.github.lakes777.vigil.verificacao.Verificacao;
import io.github.lakes777.vigil.verificacao.VerificacaoRepository;

/**
 * O resumo de uma semana montada à mão (28/09 a 04/10/2026), com o Postgres de verdade
 * e um Telegram falso (WireMock).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ResumoSemanalTest {

	private static final String ENVIO = "/bot123:abc/sendMessage";
	private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
	private static final LocalDate SEMANA = LocalDate.of(2026, 9, 28);

	@RegisterExtension
	static WireMockExtension telegram = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@DynamicPropertySource
	static void telegramFalso(DynamicPropertyRegistry propriedades) {
		propriedades.add("vigil.alerta.telegram.url", telegram::baseUrl);
		propriedades.add("vigil.alerta.telegram.token", () -> "123:abc");
		propriedades.add("vigil.alerta.telegram.chat", () -> "42");
	}

	@Autowired
	private ResumoSemanal resumo;

	@Autowired
	private ResumoRepository marcas;

	@Autowired
	private ServicoRepository servicos;

	@Autowired
	private VerificacaoRepository verificacoes;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MockMvc mvc;

	@Value("${vigil.admin.chave}")
	private String chave;

	@BeforeEach
	void preparar() {
		servicos.deleteAll();
		jdbc.sql("delete from resumo_semanal").update();
		telegram.stubFor(post(ENVIO).willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));
	}

	private static Instant brasilia(String dataEHora) {
		return LocalDateTime.parse(dataEHora).atZone(BRASILIA).toInstant();
	}

	private Servico servico(String nome, boolean ativo) {
		return servicos.save(new Servico(nome, "https://" + nome.toLowerCase() + ".exemplo.com", 300, ativo));
	}

	private void noAr(Servico servico, String quando, int tempoMs) {
		verificacoes.save(new Verificacao(servico, brasilia(quando), new ResultadoSonda(true, 200, tempoMs, null)));
	}

	private void fora(Servico servico, String quando) {
		verificacoes.save(new Verificacao(servico, brasilia(quando),
				new ResultadoSonda(false, null, 10_000, "tempo esgotado (10 s)")));
	}

	/** Hanami cai 35 min na terça; Spendwise vem de uma queda que começou no domingo anterior. */
	private void semanaComQuedas() {
		Servico hanami = servico("Hanami", true);
		fora(hanami, "2026-09-27T23:00"); // antes da semana: não conta
		noAr(hanami, "2026-09-29T10:00", 300);
		fora(hanami, "2026-09-29T10:05");
		fora(hanami, "2026-09-29T10:10");
		noAr(hanami, "2026-09-29T10:40", 500);
		fora(hanami, "2026-09-30T12:00"); // falha solta (Render acordando): não é queda
		noAr(hanami, "2026-09-30T12:05", 400);
		noAr(hanami, "2026-10-05T08:00", 9_000); // depois da semana: não conta

		Servico spendwise = servico("Spendwise", true);
		fora(spendwise, "2026-09-27T23:50");
		fora(spendwise, "2026-09-27T23:55");
		fora(spendwise, "2026-09-28T00:10");
		noAr(spendwise, "2026-09-28T00:30", 200);
		noAr(spendwise, "2026-09-28T01:00", 200);

		servico("Coursebook", true); // ativo, mas sem nenhuma verificação na semana
		servico("Tidy", false); // pausado a semana toda: fica de fora
	}

	@Test
	void montaOsNumerosDaSemana() {
		semanaComQuedas();

		ResumoDaSemana semana = resumo.montar(brasilia("2026-10-08T15:00"));

		assertThat(semana.inicio()).isEqualTo(SEMANA);
		assertThat(semana.fim()).isEqualTo(LocalDate.of(2026, 10, 4));
		assertThat(semana.servicos())
				.extracting(ResumoDaSemana.Linha::nome, ResumoDaSemana.Linha::disponibilidade,
						ResumoDaSemana.Linha::verificacoes, ResumoDaSemana.Linha::quedas,
						ResumoDaSemana.Linha::segundosFora, ResumoDaSemana.Linha::tempoMedioMs)
				.containsExactly(
						tuple("Coursebook", null, 0L, 0, 0L, null),
						tuple("Hanami", 50.0, 6L, 1, 35 * 60L, 400),
						tuple("Spendwise", 66.66, 3L, 1, 30 * 60L, 200));
		assertThat(semana.maiorQueda()).isEqualTo(
				new ResumoDaSemana.MaiorQueda("Hanami", brasilia("2026-09-29T10:05"), 35 * 60L));
		assertThat(semana.texto()).isEqualTo("""
				Vigil: resumo da semana de 28/09 a 04/10.

				Coursebook: sem verificações na semana.
				Hanami: 50% no ar, 1 queda (35 min fora), resposta média de 400 ms.
				Spendwise: 66,66% no ar, 1 queda (30 min fora), resposta média de 200 ms.

				Maior queda: Hanami, 35 min (começou em 29/09 às 10:05).""");
	}

	@Test
	void quedaQueContinuaForaNoFimDaSemanaContaAteDomingoMeiaNoite() {
		Servico hanami = servico("Hanami", true);
		noAr(hanami, "2026-10-04T22:00", 300);
		fora(hanami, "2026-10-04T23:00");
		fora(hanami, "2026-10-04T23:30");
		fora(hanami, "2026-10-05T01:00");

		ResumoDaSemana semana = resumo.montar(brasilia("2026-10-08T15:00"));

		assertThat(semana.servicos().getFirst().segundosFora()).isEqualTo(60 * 60L);
	}

	private int envios() {
		return telegram.findAll(postRequestedFor(urlEqualTo(ENVIO))).size();
	}

	@Test
	void mandaUmaVezSoPorSemana() {
		semanaComQuedas();

		assertThat(resumo.enviarSeFaltar(brasilia("2026-10-05T09:00"))).isTrue();
		assertThat(resumo.enviarSeFaltar(brasilia("2026-10-05T10:00"))).isFalse();
		assertThat(resumo.enviarSeFaltar(brasilia("2026-10-08T15:00"))).isFalse();

		assertThat(envios()).isEqualTo(1);
		telegram.verify(postRequestedFor(urlEqualTo(ENVIO))
				.withFormParam("chat_id", equalTo("42"))
				.withFormParam("text", equalTo(resumo.montar(brasilia("2026-10-05T09:00")).texto())));
		assertThat(marcas.enviado(SEMANA)).isTrue();
	}

	@Test
	void naSegundaEsperaAHoraMarcada() {
		semanaComQuedas();

		assertThat(resumo.enviarSeFaltar(brasilia("2026-10-05T08:59"))).isFalse();

		assertThat(envios()).isZero();
		assertThat(marcas.enviado(SEMANA)).isFalse();
	}

	@Test
	void tentativaAtrasadaAindaMandaASemana() {
		// A VM ficou fora a segunda inteira: na terça o resumo sai mesmo assim
		semanaComQuedas();

		assertThat(resumo.enviarSeFaltar(brasilia("2026-10-06T07:00"))).isTrue();
		assertThat(envios()).isEqualTo(1);
	}

	@Test
	void telegramForaTentaDeNovoNaProximaHora() {
		semanaComQuedas();
		telegram.stubFor(post(ENVIO).willReturn(aResponse().withStatus(502)));

		assertThat(resumo.enviarSeFaltar(brasilia("2026-10-05T09:00"))).isFalse();
		assertThat(marcas.enviado(SEMANA)).as("resumo não entregue não fica marcado").isFalse();

		telegram.stubFor(post(ENVIO).willReturn(aResponse().withStatus(200)));
		assertThat(resumo.enviarSeFaltar(brasilia("2026-10-05T10:00"))).isTrue();

		assertThat(envios()).isEqualTo(2);
		assertThat(marcas.enviado(SEMANA)).isTrue();
	}

	@Test
	void semanaSemVerificacoesNaoMandaNada() {
		servico("Hanami", true);

		assertThat(resumo.enviarSeFaltar(brasilia("2026-10-05T09:00"))).isFalse();

		assertThat(envios()).isZero();
		assertThat(marcas.enviado(SEMANA)).isFalse();
	}

	@Test
	void previaEPublica() throws Exception {
		semanaComQuedas();

		mvc.perform(get("/api/resumo-semanal"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.servicos.length()").value(3))
				.andExpect(jsonPath("$.texto").isString());
		assertThat(envios()).isZero();
	}

	@Test
	void enviarAgoraPedeAChave() throws Exception {
		mvc.perform(MockMvcRequestBuilders.post("/api/resumo-semanal/enviar")).andExpect(status().isUnauthorized());

		assertThat(envios()).isZero();
	}

	@Test
	void enviarAgoraMandaSemMarcarASemana() throws Exception {
		mvc.perform(MockMvcRequestBuilders.post("/api/resumo-semanal/enviar").with(comChave(chave)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entregue").value(true))
				.andExpect(jsonPath("$.resumo.texto").isString());

		assertThat(envios()).isEqualTo(1);
		assertThat(jdbc.sql("select count(*) from resumo_semanal").query(Long.class).single()).isZero();
	}

	@Test
	void pausadoNoMeioDaSemanaAparece() {
		Servico tidy = servico("Tidy", false);
		noAr(tidy, "2026-09-28T10:00", 100);

		ResumoDaSemana semana = resumo.montar(brasilia("2026-10-08T15:00"));

		assertThat(semana.servicos()).extracting(ResumoDaSemana.Linha::nome, ResumoDaSemana.Linha::verificacoes)
				.containsExactly(tuple("Tidy", 1L));
	}

	@Test
	void marcarDuasVezesSoPassaAPrimeira() {
		assertThat(marcas.marcar(SEMANA)).isTrue();
		assertThat(marcas.marcar(SEMANA)).isFalse();
	}

	@Test
	void enviarAgoraComOTelegramForaDizQueNaoChegou() throws Exception {
		telegram.stubFor(post(ENVIO).willReturn(aResponse().withStatus(502)));

		mvc.perform(MockMvcRequestBuilders.post("/api/resumo-semanal/enviar").with(comChave(chave)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entregue").value(false));
	}

}
