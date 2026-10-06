package io.github.lakes777.vigil.verificacao;

/** O que a Sonda descobriu ao acessar uma URL. codigoHttp e erro podem ser null. */
public record ResultadoSonda(boolean noAr, Integer codigoHttp, int tempoMs, String erro) {
}
