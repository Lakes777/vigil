package io.github.lakes777.vigil.servico;

public class UrlInvalidaException extends RuntimeException {

	public UrlInvalidaException() {
		super("a URL tem caracteres inválidos ou não tem um endereço de site");
	}

	public UrlInvalidaException(String motivo) {
		super(motivo);
	}

}
