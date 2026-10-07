package io.github.lakes777.vigil.disponibilidade;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.lakes777.vigil.servico.ServicoNaoEncontradoException;
import io.github.lakes777.vigil.servico.ServicoRepository;

@Service
public class DisponibilidadeService {

	static final int MAXIMO_DE_DIAS = 90;

	private final DisponibilidadeRepository consultas;
	private final ServicoRepository servicos;

	public DisponibilidadeService(DisponibilidadeRepository consultas, ServicoRepository servicos) {
		this.consultas = consultas;
		this.servicos = servicos;
	}

	public List<StatusServico> status() {
		return consultas.status(Instant.now());
	}

	/** Reaproveita a consulta de todos: são poucos serviços, e o SQL fica num lugar só. */
	public StatusServico resumo(Long servicoId) {
		return status().stream()
				.filter(status -> status.id().equals(servicoId))
				.findFirst()
				.orElseThrow(() -> new ServicoNaoEncontradoException(servicoId));
	}

	/** Os últimos dias, contando hoje, a partir da meia-noite de Brasília. */
	public List<Dia> dias(int dias) {
		Instant agora = Instant.now();
		ZoneId brasilia = ZoneId.of("America/Sao_Paulo");
		LocalDate primeiro = LocalDate.now(brasilia).minusDays(Math.clamp(dias, 1, MAXIMO_DE_DIAS) - 1L);
		return consultas.dias(primeiro.atStartOfDay(brasilia).toInstant(), agora);
	}

	public List<Queda> quedas(Long servicoId, int dias) {
		if (!servicos.existsById(servicoId)) {
			throw new ServicoNaoEncontradoException(servicoId);
		}
		Instant agora = Instant.now();
		Instant desde = agora.minus(Duration.ofDays(Math.clamp(dias, 1, MAXIMO_DE_DIAS)));
		return consultas.quedas(servicoId, desde, agora);
	}

}
