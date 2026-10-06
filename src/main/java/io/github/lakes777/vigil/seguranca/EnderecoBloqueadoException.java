package io.github.lakes777.vigil.seguranca;

public class EnderecoBloqueadoException extends RuntimeException {

	public EnderecoBloqueadoException() {
		super("endereço bloqueado (rede interna)");
	}

}
