package io.github.lakes777.vigil.verificacao;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

/** A Sonda contra um servidor falso (WireMock), que responde o que cada teste mandar. */
class SondaTest {

	@RegisterExtension
	static WireMockExtension site = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	private final Sonda sonda = new Sonda(Duration.ofMillis(500));

	@Test
	void respostaOkEstaNoAr() {
		site.stubFor(get("/").willReturn(aResponse().withStatus(200).withBody("ok")));

		ResultadoSonda resultado = sonda.sondar(site.baseUrl() + "/");

		assertThat(resultado.noAr()).isTrue();
		assertThat(resultado.codigoHttp()).isEqualTo(200);
		assertThat(resultado.erro()).isNull();
		site.verify(getRequestedFor(urlEqualTo("/")).withHeader("User-Agent", WireMock.containing("Vigil")));
	}

	@Test
	void erroDoServidorEstaFora() {
		site.stubFor(get("/").willReturn(aResponse().withStatus(503)));

		ResultadoSonda resultado = sonda.sondar(site.baseUrl() + "/");

		assertThat(resultado.noAr()).isFalse();
		assertThat(resultado.codigoHttp()).isEqualTo(503);
		assertThat(resultado.erro()).isEqualTo("respondeu com o código 503");
	}

	@Test
	void naoEncontradoEstaFora() {
		site.stubFor(get("/sumiu").willReturn(aResponse().withStatus(404)));

		assertThat(sonda.sondar(site.baseUrl() + "/sumiu").noAr()).isFalse();
	}

	@Test
	void segueORedirecionamento() {
		site.stubFor(get("/antigo").willReturn(aResponse().withStatus(301).withHeader("Location", "/novo")));
		site.stubFor(get("/novo").willReturn(aResponse().withStatus(200)));

		ResultadoSonda resultado = sonda.sondar(site.baseUrl() + "/antigo");

		assertThat(resultado.noAr()).isTrue();
		assertThat(resultado.codigoHttp()).isEqualTo(200);
	}

	@Test
	void medeOTempoDeResposta() {
		site.stubFor(get("/").willReturn(aResponse().withStatus(200).withFixedDelay(200)));

		// Teto folgado: no CI criar a conexão pode demorar
		assertThat(sonda.sondar(site.baseUrl() + "/").tempoMs()).isBetween(200, 1500);
	}

	@Test
	void corpoMandadoDevagarNaoPrendeAVerificacao() {
		// Manda o conteúdo aos pouquinhos ao longo de 5 s (os cabeçalhos vêm com o 1º pedaço).
		// Com limite de 2 s, ler o corpo inteiro levaria os 5 s.
		site.stubFor(get("/").willReturn(aResponse().withStatus(200).withBody("x".repeat(50))
				.withChunkedDribbleDelay(10, 5000)));
		Sonda sondaDe2Segundos = new Sonda(Duration.ofSeconds(2));

		ResultadoSonda resultado = sondaDe2Segundos.sondar(site.baseUrl() + "/");

		assertThat(resultado.erro()).isNull();
		assertThat(resultado.noAr()).isTrue();
		assertThat(resultado.tempoMs()).isLessThan(2000);
	}

	@Test
	void siteLentoDemaisEsgotaOTempo() {
		site.stubFor(get("/").willReturn(aResponse().withStatus(200).withFixedDelay(2000)));

		ResultadoSonda resultado = sonda.sondar(site.baseUrl() + "/");

		assertThat(resultado.noAr()).isFalse();
		assertThat(resultado.codigoHttp()).isNull();
		assertThat(resultado.erro()).startsWith("tempo esgotado");
		assertThat(resultado.tempoMs()).isLessThan(2000);
	}

	@Test
	void portaFechadaNaoConecta() throws IOException {
		int portaLivre;
		try (ServerSocket socket = new ServerSocket(0)) {
			portaLivre = socket.getLocalPort();
		}

		ResultadoSonda resultado = sonda.sondar("http://localhost:" + portaLivre + "/");

		assertThat(resultado.noAr()).isFalse();
		assertThat(resultado.erro()).isEqualTo("não foi possível conectar");
	}

	@Test
	void urlInvalidaNaoLancaExcecao() {
		ResultadoSonda resultado = sonda.sondar("http://com espaço.com");

		assertThat(resultado.noAr()).isFalse();
		assertThat(resultado.erro()).isEqualTo("URL inválida");
	}

}
