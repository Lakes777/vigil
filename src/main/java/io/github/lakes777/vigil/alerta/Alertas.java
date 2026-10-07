package io.github.lakes777.vigil.alerta;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Decide, depois de cada verificação, se é hora de avisar que um serviço caiu ou voltou.
 *
 * Caiu: só depois de vigil.alerta.falhas-seguidas falhas seguidas (padrão 2). Sites no
 * plano grátis do Render dormem, e a primeira visita passa do tempo-limite; com um aviso
 * por falha, o celular tocaria à toa várias vezes por dia.
 * Voltou: na primeira verificação no ar depois de um "caiu", com quanto tempo ficou fora.
 * Enquanto continua fora, não repete o aviso.
 *
 * "Seguidas" quer dizer sem buraco: entre uma falha e a anterior, no máximo dois intervalos
 * do serviço (mais 1 min de folga). Assim uma falha de antes de a API ficar desligada (ou de
 * o serviço ficar pausado) não se soma a uma de agora num "caiu" com data velha.
 *
 * Caso raro aceito: se a verificação manual e a do agendador se cruzarem bem na virada,
 * o "voltou" pode chegar antes do "caiu".
 */
@Service
public class Alertas {

	private static final Logger log = LoggerFactory.getLogger(Alertas.class);
	private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm 'de' dd/MM").withZone(BRASILIA);

	private final AlertaRepository repositorio;
	private final Telegram telegram;
	private final int falhasSeguidas;

	public Alertas(AlertaRepository repositorio, Telegram telegram,
			@Value("${vigil.alerta.falhas-seguidas:2}") int falhasSeguidas) {
		this.repositorio = repositorio;
		this.telegram = telegram;
		this.falhasSeguidas = Math.max(1, falhasSeguidas);
	}

	/**
	 * Chamado logo depois de gravar uma verificação. Um problema aqui (banco, Telegram)
	 * nunca derruba a verificação: só vai para o log.
	 */
	public void avaliar(Long servicoId, String nome, int intervaloSegundos, boolean noAr, String erro) {
		try {
			if (noAr) {
				avaliarVolta(servicoId, nome);
			} else {
				avaliarQueda(servicoId, nome, intervaloSegundos, erro);
			}
		} catch (RuntimeException falha) {
			log.warn("Falha ao avaliar o aviso do serviço {} ({}): {}", servicoId, nome, falha.getMessage());
		}
	}

	private void avaliarQueda(Long servicoId, String nome, int intervaloSegundos, String erro) {
		Duration folga = Duration.ofSeconds(2L * intervaloSegundos).plusMinutes(1);
		List<Instant> sequencia = sequenciaAtual(repositorio.falhasDesdeOUltimoOk(servicoId), folga);
		if (sequencia.size() < falhasSeguidas) {
			return;
		}
		// O começo da sequência, e não só a mais antiga das 2 últimas: se o Telegram falhou
		// e o aviso só sai na 4ª falha, o "fora desde" continua sendo o da 1ª
		Instant desde = sequencia.getLast();
		if (repositorio.abrir(servicoId, desde) && !telegram.enviar(textoCaiu(nome, erro, desde))) {
			// Não chegou: desfaz, e a próxima verificação (se ainda estiver fora) tenta de novo
			repositorio.desfazerAbertura(servicoId, desde);
		}
	}

	private void avaliarVolta(Long servicoId, String nome) {
		repositorio.fechar(servicoId).ifPresent(desde -> {
			if (!telegram.enviar(textoVoltou(nome, desde, Instant.now()))) {
				repositorio.desfazerFechamento(servicoId, desde);
			}
		});
	}

	/**
	 * Das falhas (mais recente primeiro), as que formam a sequência atual: para na
	 * primeira distância maior que a folga.
	 */
	static List<Instant> sequenciaAtual(List<Instant> falhas, Duration folga) {
		List<Instant> sequencia = new ArrayList<>();
		for (Instant falha : falhas) {
			if (!sequencia.isEmpty() && Duration.between(falha, sequencia.getLast()).compareTo(folga) > 0) {
				break;
			}
			sequencia.add(falha);
		}
		return sequencia;
	}

	static String textoCaiu(String nome, String erro, Instant desde) {
		String motivo = erro == null || erro.isBlank() ? "" : " (" + erro + ")";
		return "Vigil: " + nome + " caiu" + motivo + ". Fora desde " + HORA.format(desde) + ".";
	}

	static String textoVoltou(String nome, Instant desde, Instant agora) {
		return "Vigil: " + nome + " voltou. Ficou fora por " + duracao(Duration.between(desde, agora))
				+ " (desde " + HORA.format(desde) + ").";
	}

	/** 45 s -> "menos de 1 min"; 125 min -> "2 h 05 min"; 26 h -> "1 d 2 h". */
	static String duracao(Duration tempo) {
		long minutos = tempo.toMinutes();
		if (minutos < 1) {
			return "menos de 1 min";
		}
		if (minutos < 60) {
			return minutos + " min";
		}
		long horas = tempo.toHours();
		if (horas < 24) {
			return horas + " h " + String.format("%02d", minutos % 60) + " min";
		}
		return tempo.toDays() + " d " + (horas % 24) + " h";
	}

}
