package io.github.lakes777.vigil.alerta;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Manda uma mensagem pela API do Telegram com o token do Sidekick: o aviso chega na
 * conversa com o bot, sem mudar nada nele (só ler mensagens é que daria conflito).
 * Sem token ou sem chat configurado, a mensagem vai só para o log.
 */
@Component
public class Telegram {

	private static final Logger log = LoggerFactory.getLogger(Telegram.class);

	/** O formato dos tokens do BotFather. Também barra aspas e espaços que vêm de um .env mal escrito. */
	private static final Pattern FORMATO_DO_TOKEN = Pattern.compile("^\\d+:[A-Za-z0-9_-]+$");

	private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final String endereco;
	private final String token;
	private final String chat;

	public Telegram(@Value("${vigil.alerta.telegram.url:https://api.telegram.org}") String endereco,
			@Value("${vigil.alerta.telegram.token:}") String token,
			@Value("${vigil.alerta.telegram.chat:}") String chat) {
		this.endereco = endereco;
		String limpo = token.strip();
		if (!limpo.isEmpty() && !FORMATO_DO_TOKEN.matcher(limpo).matches()) {
			// O valor nunca vai para o log: é a senha do bot
			log.warn("Token do Telegram em formato inválido: os avisos vão só para o log");
			limpo = "";
		}
		this.token = limpo;
		this.chat = chat.strip();
		if (!configurado()) {
			log.warn("Telegram sem token ou chat (TELEGRAM_TOKEN, TELEGRAM_CHAT_ID): os avisos vão só para o log");
		}
	}

	public boolean configurado() {
		return !token.isEmpty() && !chat.isEmpty();
	}

	/** Devolve true se a mensagem foi entregue (ou se não há Telegram configurado, só o log). */
	public boolean enviar(String texto) {
		log.info("Aviso: {}", texto);
		if (!configurado()) {
			return true;
		}
		String corpo = "chat_id=" + codificar(chat) + "&text=" + codificar(texto);
		HttpRequest pedido = HttpRequest.newBuilder(URI.create(endereco + "/bot" + token + "/sendMessage"))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(corpo))
				.build();
		try {
			HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
			if (resposta.statusCode() == 200) {
				return true;
			}
			// O corpo explica o erro (ex.: "chat not found"); o token nunca vai para o log
			log.warn("O Telegram recusou o aviso ({}): {}", resposta.statusCode(), resposta.body());
		} catch (IOException | IllegalArgumentException erro) {
			// Só o tipo do erro: a mensagem poderia trazer a URL, que contém o token
			log.warn("Não foi possível falar com o Telegram: {}", erro.getClass().getSimpleName());
		} catch (InterruptedException erro) {
			Thread.currentThread().interrupt();
		}
		return false;
	}

	private static String codificar(String valor) {
		return URLEncoder.encode(valor, StandardCharsets.UTF_8);
	}

}
