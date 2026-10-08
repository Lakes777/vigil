package io.github.lakes777.vigil.alerta;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/resumo-semanal")
@Tag(name = "Resumo semanal", description = "O resumo da última semana que vai pelo Telegram toda segunda")
public class ResumoController {

	private final ResumoSemanal resumo;

	public ResumoController(ResumoSemanal resumo) {
		this.resumo = resumo;
	}

	@GetMapping
	@Operation(summary = "O resumo da última semana inteira (segunda a domingo, horário de Brasília), sem mandar")
	public ResumoDaSemana previa() {
		return resumo.montar(Instant.now());
	}

	@PostMapping("/enviar")
	@SecurityRequirement(name = "chave")
	@Operation(summary = "Manda o resumo da última semana pelo Telegram agora (não conta como o envio de segunda)")
	public ResumoDaSemana.Envio enviar() {
		return resumo.enviarAgora(Instant.now());
	}

}
