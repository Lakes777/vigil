package io.github.lakes777.vigil.disponibilidade;

import java.time.Instant;

public record StatusServico(Long id, String nome, Situacao situacao, Instant ultimaVerificacao,
		Periodo ultimas24h, Periodo ultimos7d, Periodo ultimos30d) {
}
