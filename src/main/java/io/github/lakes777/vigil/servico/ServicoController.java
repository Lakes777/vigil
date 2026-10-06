package io.github.lakes777.vigil.servico;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** As rotas HTTP do cadastro. Só traduz HTTP para chamadas ao ServicoService e de volta. */
@RestController
@RequestMapping("/api/servicos")
@Tag(name = "Serviços", description = "Cadastro dos sites e APIs monitorados")
public class ServicoController {

	private final ServicoService servicos;

	public ServicoController(ServicoService servicos) {
		this.servicos = servicos;
	}

	@GetMapping
	@Operation(summary = "Lista os serviços em ordem alfabética")
	public List<ServicoResposta> listar() {
		return servicos.listar();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Busca um serviço pelo id")
	public ServicoResposta buscar(@PathVariable Long id) {
		return servicos.buscar(id);
	}

	@PostMapping
	@Operation(summary = "Cadastra um serviço")
	@SecurityRequirement(name = "chave")
	public ResponseEntity<ServicoResposta> criar(@Valid @RequestBody ServicoEntrada entrada) {
		ServicoResposta criado = servicos.criar(entrada);
		var endereco = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id()).toUri();
		return ResponseEntity.created(endereco).body(criado);
	}

	@PutMapping("/{id}")
	@SecurityRequirement(name = "chave")
	@Operation(summary = "Edita um serviço (intervalo e ativo omitidos mantêm o valor atual)")
	public ServicoResposta atualizar(@PathVariable Long id, @Valid @RequestBody ServicoEntrada entrada) {
		return servicos.atualizar(id, entrada);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Remove um serviço")
	@SecurityRequirement(name = "chave")
	public void remover(@PathVariable Long id) {
		servicos.remover(id);
	}

}
