package io.github.lakes777.vigil.verificacao;

import java.time.Duration;

public class MuitasVerificacoesException extends RuntimeException {

	private final long segundosRestantes;

	public MuitasVerificacoesException(Duration falta) {
		// Arredonda para cima: com 0,3 s faltando, "tente em 0 s" seria mentira
		this(Math.max(1, (falta.toMillis() + 999) / 1000));
	}

	private MuitasVerificacoesException(long segundosRestantes) {
		super("Este serviço acabou de ser verificado. Tente de novo em " + segundosRestantes + " s.");
		this.segundosRestantes = segundosRestantes;
	}

	public long getSegundosRestantes() {
		return segundosRestantes;
	}

}
