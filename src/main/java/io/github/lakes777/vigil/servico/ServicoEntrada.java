package io.github.lakes777.vigil.servico;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * O JSON que chega para criar ou editar um serviço. As anotações são as regras de
 * validação: se alguma falhar, a API responde 400 antes de chegar no ServicoService.
 * Intervalo e ativo são opcionais: ao criar, o padrão é 300 s e true; ao editar,
 * o que não vier mantém o valor atual (um PUT só com a URL não religa um serviço pausado).
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

		Boolean ativo) {

	static final int INTERVALO_PADRAO = 300;

	int intervaloOu(int atual) {
		return intervaloSegundos != null ? intervaloSegundos : atual;
	}

	boolean ativoOu(boolean atual) {
		return ativo != null ? ativo : atual;
	}

}
