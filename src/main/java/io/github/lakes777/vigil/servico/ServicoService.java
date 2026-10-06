package io.github.lakes777.vigil.servico;

import java.net.URI;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As regras do cadastro. Não sabe nada de HTTP: recebe e devolve objetos Java.
 * O repositório chega pelo construtor (injeção de dependência): quem cria o
 * ServicoService é o Spring, e nos testes de unidade entra um repositório falso.
 */
@Service
public class ServicoService {

	private final ServicoRepository repositorio;

	public ServicoService(ServicoRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Transactional(readOnly = true)
	public List<ServicoResposta> listar() {
		return repositorio.findAllByOrderByNomeAsc().stream().map(ServicoResposta::de).toList();
	}

	@Transactional(readOnly = true)
	public ServicoResposta buscar(Long id) {
		return ServicoResposta.de(encontrar(id));
	}

	@Transactional
	public ServicoResposta criar(ServicoEntrada entrada) {
		String nome = entrada.nome().strip();
		if (repositorio.existeComNome(nome)) {
			throw new NomeEmUsoException(nome);
		}
		validarUrl(entrada.url());
		Servico servico = new Servico(nome, entrada.url(), entrada.intervaloOu(ServicoEntrada.INTERVALO_PADRAO),
				entrada.ativoOu(true));
		return ServicoResposta.de(repositorio.save(servico));
	}

	@Transactional
	public ServicoResposta atualizar(Long id, ServicoEntrada entrada) {
		Servico servico = encontrar(id);
		String nome = entrada.nome().strip();
		if (repositorio.existeOutroComNome(nome, id)) {
			throw new NomeEmUsoException(nome);
		}
		validarUrl(entrada.url());
		// Sem save(): dentro da transação, o JPA grava sozinho o que mudou no objeto
		servico.alterar(nome, entrada.url(), entrada.intervaloOu(servico.getIntervaloSegundos()),
				entrada.ativoOu(servico.isAtivo()));
		return ServicoResposta.de(servico);
	}

	@Transactional
	public void remover(Long id) {
		repositorio.delete(encontrar(id));
	}

	/** A anotação @Pattern só confere o formato geral; aqui vale a regra de quem vai acessar a URL. */
	private static void validarUrl(String url) {
		try {
			if (URI.create(url).getHost() == null) {
				throw new UrlInvalidaException();
			}
		} catch (IllegalArgumentException erro) {
			throw new UrlInvalidaException();
		}
	}

	private Servico encontrar(Long id) {
		return repositorio.findById(id).orElseThrow(() -> new ServicoNaoEncontradoException(id));
	}

}
