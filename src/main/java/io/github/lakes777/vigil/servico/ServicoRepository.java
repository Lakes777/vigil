package io.github.lakes777.vigil.servico;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

}
