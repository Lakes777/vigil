package io.github.lakes777.vigil.disponibilidade;

import java.time.Instant;

/**
 * Uma sequência de verificações seguidas fora do ar.
 *
 * @param servico o nome do serviço (o histórico geral junta as quedas de todos)
 * @param inicio  a primeira verificação que falhou
 * @param fim     a primeira que voltou a dar certo (null se ainda está fora); se o serviço
 *                foi pausado durante a queda, a última falha vista
 * @param falhas  quantas verificações seguidas falharam (1 costuma ser só o Render acordando)
 * @param motivo  o erro da primeira falha
 */
public record Queda(Long servicoId, String servico, Instant inicio, Instant fim, long duracaoSegundos, boolean emAndamento, long falhas,
		String motivo) {
}
