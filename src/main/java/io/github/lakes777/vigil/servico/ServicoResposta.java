package io.github.lakes777.vigil.servico;

import java.time.Instant;

/** O JSON que a API devolve. Separado da entidade para o banco poder mudar sem quebrar a API. */
public record ServicoResposta(Long id, String nome, String url, int intervaloSegundos, boolean ativo,
		Instant criadoEm, int ordem, String link) {

	static ServicoResposta de(Servico servico) {
		return new ServicoResposta(servico.getId(), servico.getNome(), servico.getUrl(),
				servico.getIntervaloSegundos(), servico.isAtivo(), servico.getCriadoEm(), servico.getOrdem(),
				servico.getLink());
	}

}
