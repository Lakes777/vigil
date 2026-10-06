package io.github.lakes777.vigil;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Coloca a chave de admin num pedido do MockMvc: mvc.perform(post(...).with(comChave(chave))).
 * Cada teste lê a chave com @Value("${vigil.admin.chave}") em vez de repetir o texto,
 * porque uma chave local (config/application.properties na raiz) tem prioridade sobre a dos testes.
 */
public final class Admin {

	private Admin() {
	}

	public static RequestPostProcessor comChave(String chave) {
		return pedido -> {
			pedido.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + chave);
			return pedido;
		};
	}

}
