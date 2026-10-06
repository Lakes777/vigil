package io.github.lakes777.vigil.servico;

public class NomeEmUsoException extends RuntimeException {

	public NomeEmUsoException(String nome) {
		super("Já existe um serviço chamado \"" + nome + "\".");
	}

}
