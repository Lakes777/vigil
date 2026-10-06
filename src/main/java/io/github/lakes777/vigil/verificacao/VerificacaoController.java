package io.github.lakes777.vigil.verificacao;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/servicos/{id}")
@Tag(name = "Verificações", description = "Histórico e verificação manual de um serviço")
public class VerificacaoController {

	private final Verificador verificador;

	public VerificacaoController(Verificador verificador) {
		this.verificador = verificador;
	}

	@GetMapping("/verificacoes")
	@Operation(summary = "Últimas verificações do serviço, da mais recente para a mais antiga (limite de 1 a 500)")
	public List<VerificacaoResposta> historico(@PathVariable Long id,
			@RequestParam(defaultValue = "50") int limite) {
		return verificador.historico(id, limite);
	}

	@PostMapping("/verificar")
	@Operation(summary = "Verifica o serviço agora, sem esperar o intervalo")
	public VerificacaoResposta verificarAgora(@PathVariable Long id) {
		return verificador.verificarAgora(id);
	}

}
