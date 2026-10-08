package io.github.lakes777.vigil.disponibilidade;

/** Os números de um serviço num intervalo qualquer (o resumo da semana usa seg 0h a seg 0h). */
public record ServicoNoPeriodo(Long id, String nome, boolean ativo, Periodo periodo) {
}
