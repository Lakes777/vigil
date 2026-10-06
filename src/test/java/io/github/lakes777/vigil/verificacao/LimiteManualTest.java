package io.github.lakes777.vigil.verificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** Com um relógio que só anda quando o teste manda: nada de esperar 10 s de verdade. */
class LimiteManualTest {

	private final Relogio relogio = new Relogio();
	private final LimiteManual limite = new LimiteManual(Duration.ofSeconds(10), relogio);

	@Test
	void segundoPedidoLogoEmSeguidaEsperaOResto() {
		limite.liberar(1L);
		relogio.avancar(Duration.ofMillis(3200));

		assertThatThrownBy(() -> limite.liberar(1L))
				.isInstanceOf(MuitasVerificacoesException.class)
				.hasMessage("Este serviço acabou de ser verificado. Tente de novo em 7 s.")
				.extracting(erro -> ((MuitasVerificacoesException) erro).getSegundosRestantes())
				.isEqualTo(7L);
	}

	@Test
	void recusadoNaoReiniciaAEspera() {
		limite.liberar(1L);
		relogio.avancar(Duration.ofSeconds(9));
		assertThatThrownBy(() -> limite.liberar(1L)).isInstanceOf(MuitasVerificacoesException.class);

		relogio.avancar(Duration.ofSeconds(1));

		assertThatCode(() -> limite.liberar(1L)).doesNotThrowAnyException();
	}

	@Test
	void cadaServicoTemOProprioLimite() {
		limite.liberar(1L);

		assertThatCode(() -> limite.liberar(2L)).doesNotThrowAnyException();
	}

	@Test
	void faltandoMenosDeUmSegundoDizUm() {
		limite.liberar(1L);
		relogio.avancar(Duration.ofMillis(9999));

		assertThatThrownBy(() -> limite.liberar(1L))
				.extracting(erro -> ((MuitasVerificacoesException) erro).getSegundosRestantes())
				.isEqualTo(1L);
	}

	@Test
	void esperaZeroDesligaOLimite() {
		LimiteManual semLimite = new LimiteManual(Duration.ZERO, relogio);
		semLimite.liberar(1L);

		assertThatCode(() -> semLimite.liberar(1L)).doesNotThrowAnyException();
	}

	private static final class Relogio extends Clock {

		private Instant agora = Instant.parse("2026-10-06T12:00:00Z");

		void avancar(Duration quanto) {
			agora = agora.plus(quanto);
		}

		@Override
		public Instant instant() {
			return agora;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zona) {
			return this;
		}

	}

}
