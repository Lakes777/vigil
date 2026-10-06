package io.github.lakes777.vigil.verificacao;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface VerificacaoRepository extends JpaRepository<Verificacao, Long> {

	/** As mais recentes primeiro; o Limit vira um "limit N" no SQL. */
	List<Verificacao> findByServicoIdOrderByFeitaEmDescIdDesc(Long servicoId, Limit limite);

	@Modifying
	@Transactional
	@Query("delete from Verificacao v where v.feitaEm < :limite")
	int apagarAntesDe(Instant limite);

}
