package io.github.lakes777.vigil.disponibilidade;

import java.time.Instant;

/**
 * O tempo de resposta de um serviço numa hora: um ponto do gráfico da página de status.
 * Horas sem nenhuma verificação não aparecem.
 *
 * @param hora         o começo da hora
 * @param tempoMedioMs média só das verificações no ar (null se todas falharam)
 * @param tempoP95Ms   95% das respostas no ar foram mais rápidas que isso
 */
public record Hora(Instant hora, long verificacoes, long falhas, Integer tempoMedioMs, Integer tempoP95Ms) {
}
