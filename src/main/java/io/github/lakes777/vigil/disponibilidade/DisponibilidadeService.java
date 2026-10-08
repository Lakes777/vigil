package io.github.lakes777.vigil.disponibilidade;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.lakes777.vigil.servico.ServicoNaoEncontradoException;
import io.github.lakes777.vigil.servico.ServicoRepository;

@Service
public class DisponibilidadeService {

	static final int MAXIMO_DE_DIAS = 90;
	static final int MAXIMO_DE_HORAS = 7 * 24;

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

	public List<Queda> quedas(int dias) {
		Instant agora = Instant.now();
		return consultas.quedas(agora.minus(Duration.ofDays(Math.clamp(dias, 1, MAXIMO_DE_DIAS))), agora);
	}

	/** Os números de cada serviço de desde (incluído) até ate (fora), para o resumo da semana. */
	public List<ServicoNoPeriodo> entre(Instant desde, Instant ate) {
		return consultas.entre(desde, ate);
	}

	/**
	 * As quedas que tocam o intervalo, vistas de ate: uma que só terminou depois aparece
	 * sem fim (em andamento), e uma que começou antes aparece com o início verdadeiro.
	 */
	public List<Queda> quedasEntre(Instant desde, Instant ate) {
		return consultas.quedas(desde, ate);
	}

	/** Contando a hora atual, ainda pela metade: 24 horas = a atual e as 23 anteriores. */
	public List<Hora> tempos(Long servicoId, int horas) {
		if (!servicos.existsById(servicoId)) {
			throw new ServicoNaoEncontradoException(servicoId);
		}
		Instant agora = Instant.now();
		Instant desde = agora.truncatedTo(ChronoUnit.HOURS)
				.minus(Duration.ofHours(Math.clamp(horas, 1, MAXIMO_DE_HORAS) - 1L));
		return consultas.tempos(servicoId, desde, agora);
	}

}
