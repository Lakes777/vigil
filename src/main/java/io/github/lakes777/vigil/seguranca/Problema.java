package io.github.lakes777.vigil.seguranca;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Os erros de segurança acontecem nos filtros, antes de chegar num controller, então o
 * TratadorDeErros não os vê. Aqui o JSON sai no mesmo formato (problem detail) na mão.
 * Os textos são fixos e sem aspas, então não precisam ser escapados.
 */
final class Problema {

	private Problema() {
	}

	static void escrever(HttpServletResponse resposta, int status, String titulo, String detalhe) throws IOException {
		resposta.setStatus(status);
		resposta.setContentType("application/problem+json");
		resposta.setCharacterEncoding("UTF-8");
		if (status == HttpServletResponse.SC_UNAUTHORIZED) {
			resposta.setHeader("WWW-Authenticate", "Bearer");
		}
		resposta.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status + ",\"title\":\"" + titulo
				+ "\",\"detail\":\"" + detalhe + "\"}");
	}

}
