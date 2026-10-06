package io.github.lakes777.vigil.verificacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * O relógio do Vigil: a cada "tique" pergunta ao Verificador quem está pendente.
 * Cada serviço tem o próprio intervalo; o tique só define de quanto em quanto tempo
 * a fila é olhada. Desligado nos testes (vigil.verificacao.ligado=false).
 */
@Component
@ConditionalOnProperty(name = "vigil.verificacao.ligado", havingValue = "true", matchIfMissing = true)
public class Agendador {

	private static final Logger log = LoggerFactory.getLogger(Agendador.class);

	private final Verificador verificador;

	public Agendador(Verificador verificador) {
		this.verificador = verificador;
	}

	@Scheduled(fixedDelayString = "${vigil.verificacao.tique:10s}")
	public void tique() {
		int verificados = verificador.verificarPendentes();
		if (verificados > 0) {
			log.info("{} serviço(s) verificado(s)", verificados);
		}
	}

}
