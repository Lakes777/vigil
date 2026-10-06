package io.github.lakes777.vigil.servico;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acesso ao banco. Só a interface: o Spring Data cria a implementação sozinho,
 * incluindo save, findById e deleteById, e deduz as consultas pelo nome dos métodos.
 */
public interface ServicoRepository extends JpaRepository<Servico, Long> {

	List<Servico> findAllByOrderByNomeAsc();

	// lower() igual ao do índice servico_nome_unico (o IgnoreCase do Spring Data usaria upper())
	@Query("select count(s) > 0 from Servico s where lower(s.nome) = lower(:nome)")
	boolean existeComNome(String nome);

	@Query("select count(s) > 0 from Servico s where lower(s.nome) = lower(:nome) and s.id <> :id")
	boolean existeOutroComNome(String nome, Long id);

	/** Ativos cuja hora de verificar já chegou. */
	@Query("select s from Servico s where s.ativo and s.proximaVerificacao <= :agora order by s.proximaVerificacao")
	List<Servico> pendentes(Instant agora);

	/**
	 * Marca a próxima verificação para "agora + intervalo atual" depois de uma verificação.
	 * Só grava se proxima_verificacao ainda for a que foi lida antes da verificação: se um PUT
	 * mudou o serviço no meio tempo (e pediu para verificar logo), o pedido dele vale.
	 * Devolve quantas linhas mudou (0 ou 1).
	 */
	@Modifying
	@Transactional
	@Query(nativeQuery = true, value = """
			update servico
			set proxima_verificacao = cast(:agora as timestamptz) + intervalo_segundos * interval '1 second'
			where id = :id and proxima_verificacao = cast(:lida as timestamptz)
			""")
	int agendarDepoisDaVerificacao(Long id, Instant agora, Instant lida);

}
