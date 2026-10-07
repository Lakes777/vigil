package io.github.lakes777.vigil.alerta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class AlertasTextoTest {

	// 17:32 UTC = 14:32 em Brasília
	private static final Instant QUEDA = Instant.parse("2026-10-06T17:32:00Z");

	@Test
	void textoDeQuedaComHorarioDeBrasilia() {
		assertThat(Alertas.textoCaiu("Hanami", "tempo esgotado (10 s)", QUEDA))
				.isEqualTo("Vigil: Hanami caiu (tempo esgotado (10 s)). Fora desde 14:32 de 06/10.");
		assertThat(Alertas.textoCaiu("Hanami", null, QUEDA)).isEqualTo("Vigil: Hanami caiu. Fora desde 14:32 de 06/10.");
	}

	@Test
	void textoDeVolta() {
		assertThat(Alertas.textoVoltou("Hanami", QUEDA, QUEDA.plus(Duration.ofMinutes(12))))
				.isEqualTo("Vigil: Hanami voltou. Ficou fora por 12 min (desde 14:32 de 06/10).");
	}

	@Test
	void duracaoLegivel() {
		assertThat(Alertas.duracao(Duration.ofSeconds(45))).isEqualTo("menos de 1 min");
		assertThat(Alertas.duracao(Duration.ofMinutes(59))).isEqualTo("59 min");
		assertThat(Alertas.duracao(Duration.ofMinutes(125))).isEqualTo("2 h 05 min");
		assertThat(Alertas.duracao(Duration.ofHours(26))).isEqualTo("1 d 2 h");
	}

	@Test
	void sequenciaParaNoPrimeiroBuraco() {
		Duration folga = Duration.ofMinutes(11);
		Instant agora = QUEDA;
		List<Instant> falhas = List.of(agora, agora.minus(Duration.ofMinutes(5)), agora.minus(Duration.ofMinutes(10)),
				agora.minus(Duration.ofDays(3)));

		assertThat(Alertas.sequenciaAtual(falhas, folga)).hasSize(3).last().isEqualTo(agora.minus(Duration.ofMinutes(10)));
		assertThat(Alertas.sequenciaAtual(List.of(), folga)).isEmpty();
		assertThat(Alertas.sequenciaAtual(List.of(agora, agora.minus(Duration.ofMinutes(12))), folga)).hasSize(1);
	}

	@Test
	void problemaNoAvisoNuncaDerrubaAVerificacao() {
		AlertaRepository quebrado = mock(AlertaRepository.class);
		when(quebrado.falhasDesdeOUltimoOk(anyLong())).thenThrow(new IllegalStateException("banco fora"));
		when(quebrado.fechar(anyLong())).thenThrow(new IllegalStateException("banco fora"));
		Alertas alertas = new Alertas(quebrado, new Telegram("http://127.0.0.1:9", "", ""), 2);

		alertas.avaliar(1L, "Hanami", 300, false, "erro");
		alertas.avaliar(1L, "Hanami", 300, true, null);
	}

	@Test
	void tokenEmFormatoInvalidoNaoEUsado() {
		assertThat(new Telegram("http://127.0.0.1:9", "\"123:abc\"", "42").configurado()).isFalse();
		assertThat(new Telegram("http://127.0.0.1:9", "123:abc def", "42").configurado()).isFalse();
		assertThat(new Telegram("http://127.0.0.1:9", "123456:AAH-x_9", "42").configurado()).isTrue();
	}

	@Test
	void semTelegramConfiguradoSoRegistraNoLog() {
		Telegram semToken = new Telegram("http://127.0.0.1:9", "", "");

		assertThat(semToken.configurado()).isFalse();
		assertThat(semToken.enviar("teste")).isTrue();
	}

}
