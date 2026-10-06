package io.github.lakes777.vigil.verificacao;

import java.time.Instant;

public record VerificacaoResposta(Long id, Instant feitaEm, boolean noAr, Integer codigoHttp, int tempoMs,
		String erro) {

	static VerificacaoResposta de(Verificacao verificacao) {
		return new VerificacaoResposta(verificacao.getId(), verificacao.getFeitaEm(), verificacao.isNoAr(),
				verificacao.getCodigoHttp(), verificacao.getTempoMs(), verificacao.getErro());
	}

}
