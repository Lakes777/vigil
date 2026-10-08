package io.github.lakes777.vigil.alerta;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * O resumo de uma semana (segunda a domingo, horário de Brasília), como vai pelo Telegram.
 *
 * @param maiorQueda null se nenhum serviço caiu na semana
 * @param texto      a mensagem pronta
 */
public record ResumoDaSemana(LocalDate inicio, LocalDate fim, List<Linha> servicos, MaiorQueda maiorQueda,
		String texto) {

	/**
	 * Um serviço na semana. Só contam como queda as que dariam aviso (2 falhas seguidas ou
	 * mais): a falha solta de um site do Render acordando entra só na disponibilidade.
	 *
	 * @param disponibilidade null se não houve nenhuma verificação
	 * @param segundosFora    somando as quedas, só a parte dentro da semana
	 * @param tempoMedioMs    null se nenhuma verificação deu certo
	 */
	public record Linha(String nome, Double disponibilidade, long verificacoes, int quedas, long segundosFora,
			Integer tempoMedioMs) {
	}

	/** @param inicio o começo de verdade, mesmo que tenha sido antes da semana */
	public record MaiorQueda(String servico, Instant inicio, long segundosFora) {
	}

	/** @param entregue false se o Telegram recusou ou não está configurado */
	public record Envio(boolean entregue, ResumoDaSemana resumo) {
	}

}
