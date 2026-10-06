package io.github.lakes777.vigil.verificacao;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificacaoRepository extends JpaRepository<Verificacao, Long> {

	/** As mais recentes primeiro; o Limit vira um "limit N" no SQL. */
	List<Verificacao> findByServicoIdOrderByFeitaEmDescIdDesc(Long servicoId, Limit limite);

}
