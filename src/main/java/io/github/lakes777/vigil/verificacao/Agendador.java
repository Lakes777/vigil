package io.github.lakes777.vigil.verificacao;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.lakes777.vigil.alerta.ResumoSemanal;

/**
 * O relógio do Vigil: a cada "tique" pergunta ao Verificador quem está pendente.
 * Cada serviço tem o próprio intervalo; o tique só define de quanto em quanto tempo
 * a fila é olhada. Também apaga o histórico antigo uma vez por dia e manda o resumo da semana.
 * Desligado nos testes (vigil.verificacao.ligado=false).
 */
@Component
@ConditionalOnProperty(name = "vigil.verificacao.ligado", havingValue = "true", matchIfMissing = true)
public class Agendador {

	private static final Logger log = LoggerFactory.getLogger(Agendador.class);

	private final Verificador verificador;
	private final ResumoSemanal resumo;

	public Agendador(Verificador verificador, ResumoSemanal resumo) {
		this.verificador = verificador;
		this.resumo = resumo;
	}

	@Scheduled(fixedDelayString = "${vigil.verificacao.tique:10s}")
	public void tique() {
		int verificados = verificador.verificarPendentes();
		if (verificados > 0) {
			log.info("{} serviço(s) verificado(s)", verificados);
		}
	}

	/** Todo dia às 4h30 de Brasília, mesmo num servidor em UTC. */
	@Scheduled(cron = "${vigil.verificacao.limpeza:0 30 4 * * *}", zone = "America/Sao_Paulo")
	public void limpar() {
		log.info("{} verificação(ões) antiga(s) apagada(s)", verificador.apagarAntigas());
	}

	/** De hora em hora: o resumo só sai uma vez por semana, mas uma tentativa perdida não perde a semana. */
	@Scheduled(cron = "${vigil.resumo.tentativas:0 0 * * * *}", zone = "America/Sao_Paulo")
	public void resumoSemanal() {
		if (resumo.enviarSeFaltar(Instant.now())) {
			log.info("Resumo da semana enviado");
		}
	}

}
