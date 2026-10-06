package io.github.lakes777.vigil.verificacao;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Acessa uma URL como um navegador faria e mede quanto tempo levou.
 * Nunca lança exceção: qualquer falha vira um ResultadoSonda com noAr = false.
 */
@Component
public class Sonda {

	private final HttpClient cliente;
	private final Duration tempoLimite;

	public Sonda(@Value("${vigil.verificacao.tempo-limite:10s}") Duration tempoLimite) {
		this.tempoLimite = tempoLimite;
		this.cliente = HttpClient.newBuilder()
				.connectTimeout(tempoLimite)
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	public ResultadoSonda sondar(String url) {
		long inicio = System.nanoTime();
		try {
			HttpRequest pedido = HttpRequest.newBuilder(URI.create(url))
					.timeout(tempoLimite)
					.header("User-Agent", "Vigil (+https://github.com/Lakes777/vigil)")
					.GET()
					.build();
			// O tempo-limite só vale até chegarem os cabeçalhos. Por isso o corpo nem é lido:
			// um site que mandasse o conteúdo devagar prenderia a verificação para sempre.
			HttpResponse<InputStream> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofInputStream());
			int codigo = resposta.statusCode();
			resposta.body().close();
			// 2xx e 3xx contam como no ar; 4xx e 5xx, como fora
			boolean noAr = codigo < 400;
			return new ResultadoSonda(noAr, codigo, desde(inicio), noAr ? null : "respondeu com o código " + codigo);
		} catch (HttpTimeoutException erro) {
			return falha(inicio, "tempo esgotado (" + tempoLimite.toSeconds() + " s)");
		} catch (ConnectException erro) {
			return falha(inicio, "não foi possível conectar");
		} catch (IOException erro) {
			return falha(inicio, "erro de rede (" + erro.getClass().getSimpleName() + ")");
		} catch (IllegalArgumentException erro) {
			return falha(inicio, "URL inválida");
		} catch (InterruptedException erro) {
			Thread.currentThread().interrupt();
			return falha(inicio, "verificação interrompida");
		}
	}

	private static ResultadoSonda falha(long inicio, String motivo) {
		return new ResultadoSonda(false, null, desde(inicio), motivo);
	}

	private static int desde(long inicio) {
		return (int) Duration.ofNanos(System.nanoTime() - inicio).toMillis();
	}

}
