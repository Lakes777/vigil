package io.github.lakes777.vigil.disponibilidade;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Disponibilidade", description = "Situação atual, disponibilidade em % e quedas")
public class DisponibilidadeController {

	private final DisponibilidadeService disponibilidade;

	public DisponibilidadeController(DisponibilidadeService disponibilidade) {
		this.disponibilidade = disponibilidade;
	}

	@GetMapping("/api/status")
	@Operation(summary = "Todos os serviços: situação atual e disponibilidade em 24 h, 7 dias e 30 dias")
	public List<StatusServico> status() {
		return disponibilidade.status();
	}

	@GetMapping("/api/status/dias")
	@Operation(summary = "Disponibilidade de cada serviço por dia (horário de Brasília), contando hoje; dias de 1 a 90")
	public List<Dia> dias(@RequestParam(defaultValue = "30") int dias) {
		return disponibilidade.dias(dias);
	}

	@GetMapping("/api/servicos/{id}/resumo")
	@Operation(summary = "Situação atual e disponibilidade de um serviço")
	public StatusServico resumo(@PathVariable Long id) {
		return disponibilidade.resumo(id);
	}

	@GetMapping("/api/servicos/{id}/quedas")
	@Operation(summary = "Quedas (verificações seguidas fora do ar), da mais recente; dias de 1 a 90")
	public List<Queda> quedas(@PathVariable Long id, @RequestParam(defaultValue = "30") int dias) {
		return disponibilidade.quedas(id, dias);
	}

	@GetMapping("/api/quedas")
	@Operation(summary = "Quedas de todos os serviços (histórico de incidentes), da mais recente; dias de 1 a 90")
	public List<Queda> todasAsQuedas(@RequestParam(defaultValue = "30") int dias) {
		return disponibilidade.quedas(dias);
	}

	@GetMapping("/api/servicos/{id}/tempos")
	@Operation(summary = "Tempo de resposta hora a hora (média e p95 das verificações no ar), contando a hora atual; horas de 1 a 168")
	public List<Hora> tempos(@PathVariable Long id, @RequestParam(defaultValue = "24") int horas) {
		return disponibilidade.tempos(id, horas);
	}

}
