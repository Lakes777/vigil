package io.github.lakes777.vigil.disponibilidade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PeriodoTest {

	@Test
	void porcentagemComDuasCasas() {
		assertThat(Periodo.porcentagem(3, 4)).isEqualTo(75.0);
		assertThat(Periodo.porcentagem(4, 7)).isEqualTo(57.14);
		assertThat(Periodo.porcentagem(7, 7)).isEqualTo(100.0);
		assertThat(Periodo.porcentagem(0, 5)).isEqualTo(0.0);
	}

	@Test
	void arredondaParaBaixoParaNuncaMostrar100ComQueda() {
		assertThat(Periodo.porcentagem(19_999, 20_000)).isEqualTo(99.99);
		assertThat(Periodo.porcentagem(199_999, 200_000)).isEqualTo(99.99);
		assertThat(Periodo.porcentagem(2, 3)).isEqualTo(66.66);
	}

	@Test
	void semVerificacoesNaoTemPorcentagem() {
		assertThat(Periodo.porcentagem(0, 0)).isNull();
		Periodo vazio = Periodo.de(0, 0, null, null);
		assertThat(vazio.disponibilidade()).isNull();
		assertThat(vazio.tempoMedioMs()).isNull();
	}

	@Test
	void contaAsFalhasEArredondaOsTempos() {
		Periodo periodo = Periodo.de(10, 8, 412.6, 980.2);

		assertThat(periodo.falhas()).isEqualTo(2);
		assertThat(periodo.tempoMedioMs()).isEqualTo(413);
		assertThat(periodo.tempoP95Ms()).isEqualTo(980);
	}

}
