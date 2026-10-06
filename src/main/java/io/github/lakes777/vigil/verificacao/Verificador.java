package io.github.lakes777.vigil.verificacao;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.lakes777.vigil.servico.Servico;
import io.github.lakes777.vigil.servico.ServicoNaoEncontradoException;
import io.github.lakes777.vigil.servico.ServicoRepository;

/**
 * Decide quem verificar, chama a Sonda e grava o resultado.
 * As chamadas HTTP ficam fora de transação: segurar uma conexão do banco
 * enquanto espera um site lento responder travaria o resto da API.
 */
@Service
public class Verificador {

	static final int LIMITE_MAXIMO = 500;

	private static final Logger log = LoggerFactory.getLogger(Verificador.class);

	private final ServicoRepository servicos;
	private final VerificacaoRepository verificacoes;
	private final Sonda sonda;

	public Verificador(ServicoRepository servicos, VerificacaoRepository verificacoes, Sonda sonda) {
		this.servicos = servicos;
		this.verificacoes = verificacoes;
		this.sonda = sonda;
	}

	/**
	 * Verifica, em paralelo, todos os serviços cuja hora chegou. Cada um ganha uma
	 * thread virtual (Java 21): são baratas, então 50 sites lentos não travam a fila.
	 * O try-with-resources só termina quando todas acabarem.
	 */
	public int verificarPendentes() {
		List<Servico> pendentes = servicos.pendentes(Instant.now());
		try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
			for (Servico servico : pendentes) {
				threads.submit(() -> verificarSemPropagarErro(servico));
			}
		}
		return pendentes.size();
	}

	/** Verificação manual (POST /api/servicos/{id}/verificar), mesmo de serviço pausado. */
	public VerificacaoResposta verificarAgora(Long servicoId) {
		Servico servico = servicos.findById(servicoId).orElseThrow(() -> new ServicoNaoEncontradoException(servicoId));
		try {
			return verificar(servico);
		} catch (DataIntegrityViolationException erro) {
			// Apagado enquanto a Sonda acessava o site: a chave estrangeira barrou a gravação
			throw new ServicoNaoEncontradoException(servicoId);
		}
	}

	@Transactional(readOnly = true)
	public List<VerificacaoResposta> historico(Long servicoId, int limite) {
		if (!servicos.existsById(servicoId)) {
			throw new ServicoNaoEncontradoException(servicoId);
		}
		return verificacoes
				.findByServicoIdOrderByFeitaEmDescIdDesc(servicoId, Limit.of(Math.clamp(limite, 1, LIMITE_MAXIMO)))
				.stream().map(VerificacaoResposta::de).toList();
	}

	private VerificacaoResposta verificar(Servico servico) {
		ResultadoSonda resultado = sonda.sondar(servico.getUrl());
		Instant agora = Instant.now();
		Verificacao salva = verificacoes.save(new Verificacao(servico, agora, resultado));
		servicos.agendarDepoisDaVerificacao(servico.getId(), agora, servico.getProximaVerificacao());
		return VerificacaoResposta.de(salva);
	}

	/** Um serviço com problema (ex.: apagado no meio da verificação) não pode derrubar os outros. */
	private void verificarSemPropagarErro(Servico servico) {
		try {
			verificar(servico);
		} catch (RuntimeException erro) {
			log.warn("Falha ao verificar o serviço {} ({}): {}", servico.getId(), servico.getNome(), erro.getMessage());
		}
	}

}
