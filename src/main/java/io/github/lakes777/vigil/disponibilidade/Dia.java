package io.github.lakes777.vigil.disponibilidade;

import java.time.LocalDate;

/**
 * A disponibilidade de um serviço num dia (no horário de Brasília): as barrinhas da página de status.
 * Dias sem nenhuma verificação não aparecem.
 */
public record Dia(Long servicoId, LocalDate dia, Double disponibilidade, long verificacoes, long falhas) {
}
