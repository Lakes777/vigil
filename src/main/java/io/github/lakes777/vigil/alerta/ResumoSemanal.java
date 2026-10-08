package io.github.lakes777.vigil.alerta;

import static java.util.stream.Collectors.groupingBy;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.lakes777.vigil.alerta.ResumoDaSemana.Linha;
import io.github.lakes777.vigil.alerta.ResumoDaSemana.MaiorQueda;
import io.github.lakes777.vigil.disponibilidade.DisponibilidadeService;
import io.github.lakes777.vigil.disponibilidade.Queda;
import io.github.lakes777.vigil.disponibilidade.ServicoNoPeriodo;

/**
 * O resumo da semana pelo Telegram: toda segunda, a partir de vigil.resumo.hora (padrão 9h),
 * a semana anterior inteira de cada serviço (disponibilidade, quedas e tempo de resposta).
 *
 * O agendador chama enviarSeFaltar de hora em hora. A semana mandada fica marcada no banco,
 * então a mensagem sai uma vez só, e se a VM estiver fora às 9h (ou o Telegram falhar) a
 * próxima tentativa manda. Uma semana sem nenhuma verificação não gera mensagem.
 */
@Service
public class ResumoSemanal {

	private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
	private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm")
			.withZone(BRASILIA);

	private final DisponibilidadeService disponibilidade;
	private final ResumoRepository marcas;
	private final Telegram telegram;
	private final int falhasSeguidas;
	private final int hora;

	public ResumoSemanal(DisponibilidadeService disponibilidade, ResumoRepository marcas, Telegram telegram,
			@Value("${vigil.alerta.falhas-seguidas:2}") int falhasSeguidas,
			@Value("${vigil.resumo.hora:9}") int hora) {
		this.disponibilidade = disponibilidade;
		this.marcas = marcas;
		this.telegram = telegram;
		this.falhasSeguidas = Math.max(1, falhasSeguidas);
		this.hora = Math.clamp(hora, 0, 23);
	}

	/** A segunda-feira da última semana inteira antes de agora (no horário de Brasília). */
	static LocalDate ultimaSemana(Instant agora) {
		return LocalDate.ofInstant(agora, BRASILIA).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
				.minusWeeks(1);
	}

	public ResumoDaSemana montar(Instant agora) {
		LocalDate inicio = ultimaSemana(agora);
		Instant desde = inicio.atStartOfDay(BRASILIA).toInstant();
		Instant ate = inicio.plusWeeks(1).atStartOfDay(BRASILIA).toInstant();
		Map<Long, List<Queda>> quedas = disponibilidade.quedasEntre(desde, ate).stream()
				.filter(queda -> queda.falhas() >= falhasSeguidas)
				.collect(groupingBy(Queda::servicoId));

		List<Linha> linhas = new ArrayList<>();
		MaiorQueda maior = null;
		for (ServicoNoPeriodo servico : disponibilidade.entre(desde, ate)) {
			if (!servico.ativo() && servico.periodo().verificacoes() == 0) {
				continue; // pausado a semana toda: não interessa
			}
			List<Queda> doServico = quedas.getOrDefault(servico.id(), List.of());
			long fora = 0;
			for (Queda queda : doServico) {
				long segundos = foraNaSemana(queda, desde, ate);
				fora += segundos;
				if (maior == null || segundos > maior.segundosFora()) {
					maior = new MaiorQueda(servico.nome(), queda.inicio(), segundos);
				}
			}
			linhas.add(new Linha(servico.nome(), servico.periodo().disponibilidade(), servico.periodo().verificacoes(),
					doServico.size(), fora, servico.periodo().tempoMedioMs()));
		}
		return new ResumoDaSemana(inicio, inicio.plusDays(6), linhas, maior, texto(inicio, linhas, maior));
	}

	/**
	 * Chamado de hora em hora. Manda o resumo da última semana se ainda não foi, a partir da
	 * hora marcada na segunda (de terça a domingo, a qualquer hora: é uma tentativa atrasada).
	 * Devolve true se mandou agora.
	 */
	public boolean enviarSeFaltar(Instant agora) {
		if (!telegram.configurado()) {
			// Sem Telegram a mensagem iria só para o log: marcar a semana a perderia de vez
			return false;
		}
		ZonedDateTime local = agora.atZone(BRASILIA);
		if (local.getDayOfWeek() == DayOfWeek.MONDAY && local.getHour() < hora) {
			return false;
		}
		LocalDate semana = ultimaSemana(agora);
		if (marcas.enviado(semana)) {
			return false;
		}
		ResumoDaSemana resumo = montar(agora);
		if (semDados(resumo) || !marcas.marcar(semana)) {
			return false;
		}
		if (telegram.enviar(resumo.texto())) {
			return true;
		}
		marcas.desmarcar(semana);
		return false;
	}

	/** Manda o resumo da última semana na hora, sem olhar nem mudar a marca (para testar). */
	public ResumoDaSemana.Envio enviarAgora(Instant agora) {
		ResumoDaSemana resumo = montar(agora);
		boolean entregue = telegram.enviar(resumo.texto()) && telegram.configurado();
		return new ResumoDaSemana.Envio(entregue, resumo);
	}

	private static boolean semDados(ResumoDaSemana resumo) {
		return resumo.servicos().stream().allMatch(linha -> linha.verificacoes() == 0);
	}

	/** Só a parte da queda dentro da semana: a que começou no domingo anterior conta a partir de segunda 0h. */
	static long foraNaSemana(Queda queda, Instant desde, Instant ate) {
		Instant comeco = queda.inicio().isBefore(desde) ? desde : queda.inicio();
		Instant fim = queda.fim() == null || queda.fim().isAfter(ate) ? ate : queda.fim();
		return Math.max(0, Duration.between(comeco, fim).toSeconds());
	}

	static String texto(LocalDate inicio, List<Linha> linhas, MaiorQueda maior) {
		StringBuilder texto = new StringBuilder("Vigil: resumo da semana de ")
				.append(DIA.format(inicio)).append(" a ").append(DIA.format(inicio.plusDays(6))).append(".\n");
		for (Linha linha : linhas) {
			texto.append('\n').append(linha(linha));
		}
		if (maior != null) {
			texto.append("\n\nMaior queda: ").append(maior.servico()).append(", ")
					.append(Alertas.duracao(Duration.ofSeconds(maior.segundosFora())))
					.append(maior.inicio().isBefore(inicio.atStartOfDay(BRASILIA).toInstant()) ? " (vinha de " : " (começou em ")
					.append(DIA_E_HORA.format(maior.inicio())).append(").");
		}
		return texto.toString();
	}

	static String linha(Linha linha) {
		if (linha.verificacoes() == 0) {
			return linha.nome() + ": sem verificações na semana.";
		}
		String quedas = switch (linha.quedas()) {
			case 0 -> "sem quedas";
			case 1 -> "1 queda";
			default -> linha.quedas() + " quedas";
		};
		if (linha.quedas() > 0) {
			quedas += " (" + Alertas.duracao(Duration.ofSeconds(linha.segundosFora())) + " fora)";
		}
		String tempo = linha.tempoMedioMs() == null ? "nenhuma resposta no ar"
				: "resposta média de " + linha.tempoMedioMs() + " ms";
		return linha.nome() + ": " + porcentagem(linha.disponibilidade()) + " no ar, " + quedas + ", " + tempo + ".";
	}

	/** 100.0 -> "100%"; 99.5 -> "99,5%"; 99.87 -> "99,87%" (já vem arredondada para baixo). */
	static String porcentagem(double valor) {
		return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR"))).format(valor) + "%";
	}

}
