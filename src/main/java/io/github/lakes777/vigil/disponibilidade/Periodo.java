package io.github.lakes777.vigil.disponibilidade;

/**
 * Números de um período (24 h, 7 dias ou 30 dias). Os campos com null não têm dado:
 * nenhuma verificação no período, ou nenhuma que deu certo (para os tempos).
 *
 * @param disponibilidade porcentagem de verificações no ar, arredondada para baixo
 * @param tempoMedioMs    média só das verificações no ar (um tempo esgotado distorceria a conta)
 * @param tempoP95Ms      95% das respostas foram mais rápidas que isso
 */
public record Periodo(Double disponibilidade, long verificacoes, long falhas, Integer tempoMedioMs,
		Integer tempoP95Ms) {

	static Periodo de(long total, long noAr, Double tempoMedio, Double tempoP95) {
		return new Periodo(porcentagem(noAr, total), total, total - noAr, arredondar(tempoMedio), arredondar(tempoP95));
	}

	/**
	 * Arredonda para baixo com 2 casas: 19999 de 20000 dá 99,99, não 100. Uma página de
	 * status que mostra 100% depois de uma queda perde a confiança de quem lê.
	 */
	static Double porcentagem(long parte, long total) {
		if (total == 0) {
			return null;
		}
		return Math.floor(parte * 10000.0 / total) / 100.0;
	}

	private static Integer arredondar(Double valor) {
		return valor == null ? null : (int) Math.round(valor);
	}

}
