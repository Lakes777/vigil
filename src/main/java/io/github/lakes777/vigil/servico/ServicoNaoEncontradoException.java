package io.github.lakes777.vigil.servico;

public class ServicoNaoEncontradoException extends RuntimeException {

	public ServicoNaoEncontradoException(Long id) {
		super("Não existe serviço com id " + id + ".");
	}

}
