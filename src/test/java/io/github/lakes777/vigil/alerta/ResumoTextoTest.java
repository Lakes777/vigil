package io.github.lakes777.vigil.alerta;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.lakes777.vigil.alerta.ResumoDaSemana.Linha;
import io.github.lakes777.vigil.alerta.ResumoDaSemana.MaiorQueda;
import io.github.lakes777.vigil.disponibilidade.Queda;

/** As contas e o texto do resumo, sem banco. */
class ResumoTextoTest {

	private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

	private static Instant brasilia(String dataEHora) {
		return LocalDateTime.parse(dataEHora).atZone(BRASILIA).toInstant();
	}

	@Test
	void ultimaSemanaEASemanaInteiraAntesDeAgora() {
		// Quinta 08/10: a última semana inteira começou na segunda 28/09
		assertThat(ResumoSemanal.ultimaSemana(brasilia("2026-10-08T15:00"))).isEqualTo(LocalDate.of(2026, 9, 28));
		// Na própria segunda já vale a semana que acabou de fechar
		assertThat(ResumoSemanal.ultimaSemana(brasilia("2026-10-05T00:00"))).isEqualTo(LocalDate.of(2026, 9, 28));
		// Domingo 23h59 ainda é a semana anterior a ela
		assertThat(ResumoSemanal.ultimaSemana(brasilia("2026-10-04T23:59"))).isEqualTo(LocalDate.of(2026, 9, 21));
	}

	@Test
	void ultimaSemanaUsaOHorarioDeBrasilia() {
		// Segunda 01h em UTC ainda é domingo 22h em Brasília
		assertThat(ResumoSemanal.ultimaSemana(Instant.parse("2026-10-05T01:00:00Z")))
				.isEqualTo(LocalDate.of(2026, 9, 21));
	}

	private static Queda queda(String inicio, String fim) {
		Instant comeco = brasilia(inicio);
		Instant volta = fim == null ? null : brasilia(fim);
		return new Queda(1L, "Hanami", comeco, volta, 0, volta == null, 3, "tempo esgotado (10 s)");
	}

	@Test
	void foraNaSemanaContaSoAParteDeDentro() {
		Instant desde = brasilia("2026-09-28T00:00");
		Instant ate = brasilia("2026-10-05T00:00");

		assertThat(ResumoSemanal.foraNaSemana(queda("2026-09-29T10:00", "2026-09-29T10:35"), desde, ate))
				.isEqualTo(35 * 60);
		assertThat(ResumoSemanal.foraNaSemana(queda("2026-09-27T23:50", "2026-09-28T00:30"), desde, ate))
				.as("começou no domingo anterior").isEqualTo(30 * 60);
		assertThat(ResumoSemanal.foraNaSemana(queda("2026-10-04T23:00", null), desde, ate))
				.as("continuava fora no fim da semana").isEqualTo(60 * 60);
		assertThat(ResumoSemanal.foraNaSemana(queda("2026-10-04T23:00", "2026-10-05T02:00"), desde, ate))
				.as("voltou só na semana seguinte").isEqualTo(60 * 60);
	}

	@Test
	void porcentagemNoFormatoBrasileiro() {
		assertThat(ResumoSemanal.porcentagem(100.0)).isEqualTo("100%");
		assertThat(ResumoSemanal.porcentagem(99.5)).isEqualTo("99,5%");
		assertThat(ResumoSemanal.porcentagem(99.87)).isEqualTo("99,87%");
		assertThat(ResumoSemanal.porcentagem(0.0)).isEqualTo("0%");
	}

	@Test
	void linhaDeCadaServico() {
		assertThat(ResumoSemanal.linha(new Linha("Hanami", 100.0, 2016, 0, 0, 312)))
				.isEqualTo("Hanami: 100% no ar, sem quedas, resposta média de 312 ms.");
		assertThat(ResumoSemanal.linha(new Linha("Hanami", 99.12, 2016, 1, 600, 312)))
				.isEqualTo("Hanami: 99,12% no ar, 1 queda (10 min fora), resposta média de 312 ms.");
		assertThat(ResumoSemanal.linha(new Linha("Hanami", 97.0, 2016, 3, 7500, 312)))
				.isEqualTo("Hanami: 97% no ar, 3 quedas (2 h 05 min fora), resposta média de 312 ms.");
		assertThat(ResumoSemanal.linha(new Linha("Hanami", 0.0, 4, 1, 1200, null)))
				.isEqualTo("Hanami: 0% no ar, 1 queda (20 min fora), nenhuma resposta no ar.");
		assertThat(ResumoSemanal.linha(new Linha("Hanami", null, 0, 0, 0, null)))
				.isEqualTo("Hanami: sem verificações na semana.");
	}

	@Test
	void textoComCabecalhoLinhasEMaiorQueda() {
		List<Linha> linhas = List.of(new Linha("Hanami", 99.5, 2016, 1, 2100, 400),
				new Linha("Spendwise", 100.0, 2016, 0, 0, 200));
		MaiorQueda maior = new MaiorQueda("Hanami", brasilia("2026-09-29T10:05"), 2100);

		assertThat(ResumoSemanal.texto(LocalDate.of(2026, 9, 28), linhas, maior)).isEqualTo("""
				Vigil: resumo da semana de 28/09 a 04/10.

				Hanami: 99,5% no ar, 1 queda (35 min fora), resposta média de 400 ms.
				Spendwise: 100% no ar, sem quedas, resposta média de 200 ms.

				Maior queda: Hanami, 35 min (começou em 29/09 às 10:05).""");
	}

	@Test
	void textoSemQuedasNaoTemAMaiorQueda() {
		List<Linha> linhas = List.of(new Linha("Spendwise", 100.0, 2016, 0, 0, 200));

		assertThat(ResumoSemanal.texto(LocalDate.of(2026, 9, 28), linhas, null)).isEqualTo("""
				Vigil: resumo da semana de 28/09 a 04/10.

				Spendwise: 100% no ar, sem quedas, resposta média de 200 ms.""");
	}

	@Test
	void maiorQuedaQueVinhaDaSemanaAnterior() {
		List<Linha> linhas = List.of(new Linha("Spendwise", 66.66, 3, 1, 1800, 200));
		MaiorQueda maior = new MaiorQueda("Spendwise", brasilia("2026-09-27T23:50"), 1800);

		assertThat(ResumoSemanal.texto(LocalDate.of(2026, 9, 28), linhas, maior))
				.endsWith("Maior queda: Spendwise, 30 min (vinha de 27/09 às 23:50).");
	}

}
