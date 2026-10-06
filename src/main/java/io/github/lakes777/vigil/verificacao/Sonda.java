package io.github.lakes777.vigil.verificacao;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.lakes777.vigil.seguranca.EnderecoBloqueadoException;
import io.github.lakes777.vigil.seguranca.FiltroDeEnderecos;

/**
 * Acessa uma URL como um navegador faria e mede quanto tempo levou.
 * Nunca lança exceção: qualquer falha vira um ResultadoSonda com noAr = false.
 *
 * Antes de cada acesso o FiltroDeEnderecos confere se o endereço não é interno (SSRF).
 * Os redirecionamentos são seguidos aqui, um a um, e não pelo HttpClient: assim cada
 * salto também passa pelo filtro (um site público poderia redirecionar para 127.0.0.1).
 */
@Component
public class Sonda {

	static final int MAXIMO_DE_SALTOS = 5;

	private static final Set<Integer> REDIRECIONAMENTOS = Set.of(301, 302, 303, 307, 308);

	private final HttpClient cliente;
	private final Duration tempoLimite;
	private final FiltroDeEnderecos filtro;

	public Sonda(@Value("${vigil.verificacao.tempo-limite:10s}") Duration tempoLimite, FiltroDeEnderecos filtro) {
		this.tempoLimite = tempoLimite;
		this.filtro = filtro;
		this.cliente = HttpClient.newBuilder()
				.connectTimeout(tempoLimite)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	public ResultadoSonda sondar(String url) {
		long inicio = System.nanoTime();
		try {
			URI endereco = URI.create(url);
			for (int salto = 0; salto <= MAXIMO_DE_SALTOS; salto++) {
				filtro.conferir(endereco);
				// O tempo-limite vale para o caminho todo, somando os redirecionamentos
				Duration restante = tempoLimite.minus(Duration.ofNanos(System.nanoTime() - inicio));
				if (restante.isNegative() || restante.isZero()) {
					return tempoEsgotado(inicio);
				}
				HttpRequest pedido = HttpRequest.newBuilder(endereco)
						.timeout(restante)
						.header("User-Agent", "Vigil (+https://github.com/Lakes777/vigil)")
						.GET()
						.build();
				// O tempo-limite só vale até chegarem os cabeçalhos. Por isso o corpo nem é lido:
				// um site que mandasse o conteúdo devagar prenderia a verificação para sempre.
				HttpResponse<InputStream> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofInputStream());
				int codigo = resposta.statusCode();
				resposta.body().close();
				var destino = resposta.headers().firstValue("Location");
				if (REDIRECIONAMENTOS.contains(codigo) && destino.isPresent()) {
					endereco = proximo(endereco, destino.get());
					continue;
				}
				// 2xx e 3xx contam como no ar; 4xx e 5xx, como fora
				boolean noAr = codigo < 400;
				return new ResultadoSonda(noAr, codigo, desde(inicio), noAr ? null : "respondeu com o código " + codigo);
			}
			return falha(inicio, "redirecionamentos demais (mais de " + MAXIMO_DE_SALTOS + ")");
		} catch (EnderecoBloqueadoException erro) {
			return falha(inicio, erro.getMessage());
		} catch (UnknownHostException erro) {
			return falha(inicio, "endereço não encontrado (DNS)");
		} catch (HttpTimeoutException erro) {
			return tempoEsgotado(inicio);
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

	/** O Location pode ser relativo ("/novo"); só segue para http e https. */
	private static URI proximo(URI atual, String location) {
		URI destino = atual.resolve(location.strip());
		String esquema = destino.getScheme();
		if (esquema == null || !(esquema.equalsIgnoreCase("http") || esquema.equalsIgnoreCase("https"))) {
			throw new IllegalArgumentException("redirecionou para algo que não é http(s)");
		}
		return destino;
	}

	private ResultadoSonda tempoEsgotado(long inicio) {
		return falha(inicio, "tempo esgotado (" + tempoLimite.toSeconds() + " s)");
	}

	private static ResultadoSonda falha(long inicio, String motivo) {
		return new ResultadoSonda(false, null, desde(inicio), motivo);
	}

	private static int desde(long inicio) {
		return (int) Duration.ofNanos(System.nanoTime() - inicio).toMillis();
	}

}
