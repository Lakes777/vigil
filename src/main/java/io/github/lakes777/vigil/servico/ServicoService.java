package io.github.lakes777.vigil.servico;

import java.net.URI;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.lakes777.vigil.seguranca.FiltroDeEnderecos;

/**
 * As regras do cadastro. Não sabe nada de HTTP: recebe e devolve objetos Java.
 * O repositório chega pelo construtor (injeção de dependência): quem cria o
 * ServicoService é o Spring, e nos testes de unidade entra um repositório falso.
 */
@Service
public class ServicoService {

	private final ServicoRepository repositorio;
	private final FiltroDeEnderecos filtro;

	public ServicoService(ServicoRepository repositorio, FiltroDeEnderecos filtro) {
		this.repositorio = repositorio;
		this.filtro = filtro;
	}

	@Transactional(readOnly = true)
	public List<ServicoResposta> listar() {
		return repositorio.findAllByOrderByOrdemAscNomeAsc().stream().map(ServicoResposta::de).toList();
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
		String link = entrada.linkOu(null);
		if (link != null) {
			validarUrl(link);
		}
		// Sem ordem, vai para o fim da lista (até o teto que a própria API aceita num PUT)
		int ordem = entrada.ordemOu(Math.min(repositorio.maiorOrdem() + 1, ServicoEntrada.ORDEM_MAXIMA));
		Servico servico = new Servico(nome, entrada.url(), entrada.intervaloOu(ServicoEntrada.INTERVALO_PADRAO),
				entrada.ativoOu(true), ordem, link);
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
		boolean pausou = servico.isAtivo() && !entrada.ativoOu(true);
		if (pausou || !servico.getUrl().equals(entrada.url())) {
			repositorio.esquecerAviso(id);
		}
		String link = entrada.linkOu(servico.getLink());
		if (link != null) {
			validarUrl(link);
		}
		int intervalo = entrada.intervaloOu(servico.getIntervaloSegundos());
		boolean ativo = entrada.ativoOu(servico.isAtivo());
		// Só a ordem ou o link mudaram: é só a página, sem verificar de novo
		boolean mudouVerificacao = !nome.equals(servico.getNome()) || !entrada.url().equals(servico.getUrl())
				|| intervalo != servico.getIntervaloSegundos() || ativo != servico.isAtivo();
		if (mudouVerificacao) {
			servico.alterar(nome, entrada.url(), intervalo, ativo);
		}
		servico.mudarApresentacao(entrada.ordemOu(servico.getOrdem()), link);
		return ServicoResposta.de(servico);
	}

	@Transactional
	public void remover(Long id) {
		repositorio.delete(encontrar(id));
	}

	/** A anotação @Pattern só confere o formato geral; aqui vale a regra de quem vai acessar a URL. */
	private void validarUrl(String url) {
		String host;
		try {
			host = URI.create(url).getHost();
		} catch (IllegalArgumentException erro) {
			throw new UrlInvalidaException();
		}
		if (host == null) {
			throw new UrlInvalidaException();
		}
		if (filtro.bloqueadoNoCadastro(host)) {
			throw new UrlInvalidaException("endereços internos (localhost, rede privada) não podem ser monitorados");
		}
	}

	private Servico encontrar(Long id) {
		return repositorio.findById(id).orElseThrow(() -> new ServicoNaoEncontradoException(id));
	}

}
