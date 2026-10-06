package io.github.lakes777.vigil.verificacao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limite do "verificar agora": no máximo uma vez a cada vigil.verificacao.espera-manual
 * por serviço. Sem ele, um script em laço (ou uma chave vazada) faria o Vigil disparar
 * pedidos sem parar contra um site, que é justamente o que um monitor não pode fazer.
 *
 * Fica na memória: com uma instância só, não precisa de banco nem de Redis.
 */
@Component
public class LimiteManual {

	private final Map<Long, Instant> ultimas = new ConcurrentHashMap<>();
	private final Duration espera;
	private final Clock relogio;

	@Autowired
	public LimiteManual(@Value("${vigil.verificacao.espera-manual:10s}") Duration espera) {
		this(espera, Clock.systemUTC());
	}

	/** Nos testes entra um relógio controlado, para não precisar esperar de verdade. */
	LimiteManual(Duration espera, Clock relogio) {
		this.espera = espera;
		this.relogio = relogio;
	}

	/** Libera e marca a hora, ou lança MuitasVerificacoesException dizendo quanto falta. */
	public void liberar(Long servicoId) {
		Instant agora = relogio.instant();
		// Quem já passou da espera não precisa mais ficar guardado
		ultimas.values().removeIf(quando -> !quando.plus(espera).isAfter(agora));
		Instant[] liberaEm = {null};
		// compute() é atômico por chave: dois pedidos juntos não passam os dois
		ultimas.compute(servicoId, (id, anterior) -> {
			if (anterior != null && anterior.plus(espera).isAfter(agora)) {
				liberaEm[0] = anterior.plus(espera);
				return anterior;
			}
			return agora;
		});
		if (liberaEm[0] != null) {
			throw new MuitasVerificacoesException(Duration.between(agora, liberaEm[0]));
		}
	}

}
