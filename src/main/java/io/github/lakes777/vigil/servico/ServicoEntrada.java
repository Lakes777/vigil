package io.github.lakes777.vigil.servico;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * O JSON que chega para criar ou editar um serviço. As anotações são as regras de
 * validação: se alguma falhar, a API responde 400 antes de chegar no ServicoService.
 * Intervalo, ativo, ordem e link são opcionais: ao criar, o padrão é 300 s, true, o fim
 * da lista e sem link; ao editar, o que não vier mantém o valor atual (um PUT só com a
 * URL não religa um serviço pausado). Link "" apaga o link (o "Abrir o site" volta à URL).
 */
public record ServicoEntrada(
		@NotBlank(message = "informe o nome")
		@Size(max = 80, message = "use no máximo 80 caracteres")
		String nome,

		@NotBlank(message = "informe a URL")
		@Size(max = 500, message = "use no máximo 500 caracteres")
		@Pattern(regexp = "^https?://[^\\s/$.?#][^\\s]*$", message = "use uma URL que comece com http:// ou https://")
		String url,

		@Min(value = 60, message = "o intervalo mínimo é de 60 segundos")
		@Max(value = 86400, message = "o intervalo máximo é de 1 dia (86400 segundos)")
		Integer intervaloSegundos,

		Boolean ativo,

		@Min(value = 0, message = "a ordem não pode ser negativa")
		@Max(value = ORDEM_MAXIMA, message = "a ordem vai até 1000")
		Integer ordem,

		@Size(max = 500, message = "use no máximo 500 caracteres")
		@Pattern(regexp = "^$|^https?://[^\\s/$.?#][^\\s]*$", message = "use um link que comece com http:// ou https://")
		String link) {

	static final int INTERVALO_PADRAO = 300;
	static final int ORDEM_MAXIMA = 1000;

	/** Sem ordem nem link: os dois ficam como estão (ou no padrão, ao criar). */
	public ServicoEntrada(String nome, String url, Integer intervaloSegundos, Boolean ativo) {
		this(nome, url, intervaloSegundos, ativo, null, null);
	}

	int intervaloOu(int atual) {
		return intervaloSegundos != null ? intervaloSegundos : atual;
	}

	boolean ativoOu(boolean atual) {
		return ativo != null ? ativo : atual;
	}

	int ordemOu(int atual) {
		return ordem != null ? ordem : atual;
	}

	/** Sem o campo, mantém o atual; "" apaga. */
	String linkOu(String atual) {
		if (link == null) {
			return atual;
		}
		return link.isBlank() ? null : link.strip();
	}

}
