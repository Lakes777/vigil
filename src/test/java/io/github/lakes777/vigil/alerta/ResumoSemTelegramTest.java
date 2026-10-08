package io.github.lakes777.vigil.alerta;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.lakes777.vigil.TestcontainersConfiguration;
import io.github.lakes777.vigil.servico.Servico;
import io.github.lakes777.vigil.servico.ServicoRepository;
import io.github.lakes777.vigil.verificacao.ResultadoSonda;
import io.github.lakes777.vigil.verificacao.Verificacao;
import io.github.lakes777.vigil.verificacao.VerificacaoRepository;

/** Sem token (ou com um token recusado), o resumo não marca a semana: ela espera o Telegram voltar. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ResumoSemTelegramTest {

	@DynamicPropertySource
	static void semTelegram(DynamicPropertyRegistry propriedades) {
		propriedades.add("vigil.alerta.telegram.token", () -> "");
	}

	@Autowired
	private ResumoSemanal resumo;

	@Autowired
	private ResumoRepository marcas;

	@Autowired
	private ServicoRepository servicos;

	@Autowired
	private VerificacaoRepository verificacoes;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void naoMarcaASemana() {
		servicos.deleteAll();
		jdbc.sql("delete from resumo_semanal").update();
		ZoneId brasilia = ZoneId.of("America/Sao_Paulo");
		Servico hanami = servicos.save(new Servico("Hanami", "https://hanami.exemplo.com", 300, true));
		verificacoes.save(new Verificacao(hanami, LocalDateTime.parse("2026-09-29T10:00").atZone(brasilia).toInstant(),
				new ResultadoSonda(true, 200, 300, null)));

		assertThat(resumo.enviarSeFaltar(LocalDateTime.parse("2026-10-05T09:00").atZone(brasilia).toInstant()))
				.isFalse();
		assertThat(marcas.enviado(LocalDate.of(2026, 9, 28))).isFalse();
	}

}
