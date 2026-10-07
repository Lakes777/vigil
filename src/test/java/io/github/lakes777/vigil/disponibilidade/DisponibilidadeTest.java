package io.github.lakes777.vigil.disponibilidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.assertj.core.data.TemporalUnitOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import io.github.lakes777.vigil.TestcontainersConfiguration;
import io.github.lakes777.vigil.servico.Servico;
import io.github.lakes777.vigil.servico.ServicoRepository;
import io.github.lakes777.vigil.verificacao.ResultadoSonda;
import io.github.lakes777.vigil.verificacao.Verificacao;
import io.github.lakes777.vigil.verificacao.VerificacaoRepository;
import io.github.lakes777.vigil.verificacao.Verificador;

/** Os números saem de um histórico montado à mão, com horários conhecidos. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DisponibilidadeTest {

	@Autowired
	private DisponibilidadeService disponibilidade;

	@Autowired
	private ServicoRepository servicos;

	@Autowired
	private VerificacaoRepository verificacoes;

	@Autowired
	private Verificador verificador;

	@Autowired
	private MockMvc mvc;

	private static final TemporalUnitOffset UM_SEGUNDO = within(1, ChronoUnit.SECONDS);

	private final Instant agora = Instant.now();

	@BeforeEach
	void limpar() {
		servicos.deleteAll();
	}

	private Servico servico(String nome, boolean ativo) {
		return servicos.save(new Servico(nome, "https://" + nome.toLowerCase() + ".exemplo.com", 300, ativo));
	}

	private void noAr(Servico servico, Duration atras, int tempoMs) {
		verificacoes.save(new Verificacao(servico, agora.minus(atras), new ResultadoSonda(true, 200, tempoMs, null)));
	}

	private void fora(Servico servico, Duration atras, String erro) {
		verificacoes.save(new Verificacao(servico, agora.minus(atras), new ResultadoSonda(false, null, 10_000, erro)));
	}

	private static Duration horas(long n) {
		return Duration.ofHours(n);
	}

	private static Duration dias(long n) {
		return Duration.ofDays(n);
	}

	@Test
	void disponibilidadeEmCadaPeriodo() {
		Servico encore = servico("Encore", true);
		// últimas 24 h: 3 no ar, 1 fora
		noAr(encore, horas(1), 100);
		noAr(encore, horas(2), 200);
		noAr(encore, horas(3), 300);
		fora(encore, horas(4), "tempo esgotado (10 s)");
		// há 3 dias: 2 fora (contam em 7 e 30 dias)
		fora(encore, dias(3), "não foi possível conectar");
		fora(encore, dias(3).plusHours(1), "não foi possível conectar");
		// há 10 dias: 1 no ar (só em 30 dias); há 40 dias: fica de fora
		noAr(encore, dias(10), 400);
		fora(encore, dias(40), "antigo");

		StatusServico status = disponibilidade.resumo(encore.getId());

		assertThat(status.situacao()).isEqualTo(Situacao.NO_AR);
		assertThat(status.ultimas24h().disponibilidade()).isEqualTo(75.0);
		assertThat(status.ultimas24h().verificacoes()).isEqualTo(4);
		assertThat(status.ultimas24h().falhas()).isEqualTo(1);
		assertThat(status.ultimos7d().disponibilidade()).isEqualTo(50.0);
		assertThat(status.ultimos30d().disponibilidade()).isEqualTo(57.14);
		assertThat(status.ultimos30d().verificacoes()).isEqualTo(7);
	}

	@Test
	void disponibilidadePorDiaNoHorarioDeBrasilia() throws Exception {
		Servico encore = servico("Encore", true);
		ZoneId brasilia = ZoneId.of("America/Sao_Paulo");
		LocalDate hoje = LocalDate.now(brasilia);
		Instant ontem22h = hoje.minusDays(1).atTime(22, 30).atZone(brasilia).toInstant();
		// 22h30 de ontem em Brasília já é "hoje" em UTC (01h30): tem que contar em ontem
		verificacoes.save(new Verificacao(encore, ontem22h, new ResultadoSonda(false, null, 10_000, "erro")));
		verificacoes.save(new Verificacao(encore, ontem22h.plusSeconds(60), new ResultadoSonda(true, 200, 100, null)));
		verificacoes.save(new Verificacao(encore, ontem22h.plusSeconds(120), new ResultadoSonda(true, 200, 100, null)));
		verificacoes.save(new Verificacao(encore, hoje.minusDays(40).atStartOfDay(brasilia).toInstant(),
				new ResultadoSonda(true, 200, 100, null)));

		List<Dia> dias = disponibilidade.dias(30);

		assertThat(dias).singleElement().satisfies(dia -> {
			assertThat(dia.dia()).isEqualTo(hoje.minusDays(1));
			assertThat(dia.disponibilidade()).isEqualTo(66.66);
			assertThat(dia.verificacoes()).isEqualTo(3);
			assertThat(dia.falhas()).isEqualTo(1);
		});
		assertThat(disponibilidade.dias(1)).isEmpty();
		mvc.perform(get("/api/status/dias?dias=7"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].dia").value(hoje.minusDays(1).toString()))
				.andExpect(jsonPath("$[0].servicoId").value(encore.getId()));
	}

	@Test
	void porDiaRespeitaOPrimeiroDiaEOLimiteDeDias() {
		Servico encore = servico("Encore", true);
		Servico hanami = servico("Hanami", true);
		ZoneId brasilia = ZoneId.of("America/Sao_Paulo");
		LocalDate hoje = LocalDate.now(brasilia);
		Instant inicioDoPrimeiroDia = hoje.minusDays(29).atStartOfDay(brasilia).toInstant();
		// Meia-noite do 1º dos 30 dias entra; um minuto antes, não
		verificacoes.save(new Verificacao(encore, inicioDoPrimeiroDia, new ResultadoSonda(true, 200, 100, null)));
		verificacoes.save(new Verificacao(encore, inicioDoPrimeiroDia.minusSeconds(60),
				new ResultadoSonda(true, 200, 100, null)));
		verificacoes.save(new Verificacao(hanami, inicioDoPrimeiroDia.plusSeconds(3600),
				new ResultadoSonda(false, null, 10_000, "erro")));

		List<Dia> dias = disponibilidade.dias(30);

		assertThat(dias).extracting(Dia::servicoId, Dia::dia, Dia::verificacoes).containsExactly(
				tuple(encore.getId(), hoje.minusDays(29), 1L),
				tuple(hanami.getId(), hoje.minusDays(29), 1L));
		assertThat(disponibilidade.dias(0)).isEmpty();
		assertThat(disponibilidade.dias(500)).hasSize(3);
	}

	@Test
	void tempoMedioUsaSoAsVerificacoesNoAr() {
		Servico hanami = servico("Hanami", true);
		noAr(hanami, horas(1), 100);
		noAr(hanami, horas(2), 300);
		fora(hanami, horas(3), "tempo esgotado (10 s)");

		Periodo dia = disponibilidade.resumo(hanami.getId()).ultimas24h();

		assertThat(dia.tempoMedioMs()).isEqualTo(200);
		assertThat(dia.tempoP95Ms()).isEqualTo(290);
	}

	@Test
	void situacaoDeCadaServico() {
		Servico noAr = servico("A no ar", true);
		noAr(noAr, horas(1), 100);
		Servico fora = servico("B fora", true);
		noAr(fora, horas(2), 100);
		fora(fora, horas(1), "respondeu com o código 503");
		Servico pausado = servico("C pausado", false);
		fora(pausado, horas(1), "qualquer");
		servico("D sem dados", true);

		List<StatusServico> todos = disponibilidade.status();

		assertThat(todos).extracting(StatusServico::situacao)
				.containsExactly(Situacao.NO_AR, Situacao.FORA, Situacao.PAUSADO, Situacao.SEM_DADOS);
		StatusServico semDados = todos.get(3);
		assertThat(semDados.ultimaVerificacao()).isNull();
		assertThat(semDados.ultimas24h().disponibilidade()).isNull();
		assertThat(semDados.ultimas24h().verificacoes()).isZero();
	}

	@Test
	void quedasSeparadasPelasVerificacoesNoAr() {
		Servico spendwise = servico("Spendwise", true);
		noAr(spendwise, horas(10), 100);
		fora(spendwise, horas(9), "tempo esgotado (10 s)");   // queda 1 começa
		fora(spendwise, horas(8), "não foi possível conectar");
		noAr(spendwise, horas(7), 100);                        // queda 1 termina
		noAr(spendwise, horas(6), 100);
		fora(spendwise, horas(2), "respondeu com o código 502"); // queda 2, ainda em andamento
		fora(spendwise, horas(1), "respondeu com o código 502");

		List<Queda> quedas = disponibilidade.quedas(spendwise.getId(), 30);

		assertThat(quedas).hasSize(2);
		Queda atual = quedas.get(0);
		assertThat(atual.emAndamento()).isTrue();
		assertThat(atual.fim()).isNull();
		assertThat(atual.falhas()).isEqualTo(2);
		assertThat(atual.motivo()).isEqualTo("respondeu com o código 502");
		assertThat(atual.duracaoSegundos()).isBetween(2 * 3600L - 5, 2 * 3600L + 5);

		Queda anterior = quedas.get(1);
		assertThat(anterior.emAndamento()).isFalse();
		assertThat(anterior.inicio()).isCloseTo(agora.minus(horas(9)), UM_SEGUNDO);
		assertThat(anterior.fim()).isCloseTo(agora.minus(horas(7)), UM_SEGUNDO);
		assertThat(anterior.duracaoSegundos()).isEqualTo(2 * 3600L);
		assertThat(anterior.falhas()).isEqualTo(2);
		assertThat(anterior.motivo()).isEqualTo("tempo esgotado (10 s)");
	}

	@Test
	void quedasRespeitamOPeriodo() {
		Servico tidy = servico("Tidy", true);
		fora(tidy, dias(20), "antiga");
		noAr(tidy, dias(19), 100);
		fora(tidy, dias(2), "recente");
		noAr(tidy, dias(1), 100);

		assertThat(disponibilidade.quedas(tidy.getId(), 7)).extracting(Queda::motivo).containsExactly("recente");
		assertThat(disponibilidade.quedas(tidy.getId(), 30)).hasSize(2);
	}

	@Test
	void quedaQueComecouAntesDoPeriodoApareceInteira() {
		Servico hanami = servico("Hanami", true);
		fora(hanami, dias(8), "caiu há 8 dias");
		fora(hanami, dias(7).plusHours(1), "segunda falha");
		fora(hanami, dias(6).plusHours(12), "terceira falha");
		noAr(hanami, dias(6), 100);

		Queda queda = disponibilidade.quedas(hanami.getId(), 7).getFirst();

		assertThat(queda.inicio()).isCloseTo(agora.minus(dias(8)), UM_SEGUNDO);
		assertThat(queda.falhas()).isEqualTo(3);
		assertThat(queda.motivo()).isEqualTo("caiu há 8 dias");
		// Terminou antes da janela de 5 dias: não entra
		assertThat(disponibilidade.quedas(hanami.getId(), 5)).isEmpty();
	}

	@Test
	void servicoPausadoDuranteAQuedaNaoFicaEmAndamentoParaSempre() {
		Servico tidy = servico("Tidy", true);
		fora(tidy, horas(5), "não foi possível conectar");
		fora(tidy, horas(4), "não foi possível conectar");
		tidy.alterar("Tidy", tidy.getUrl(), 300, false);
		servicos.save(tidy);

		Queda queda = disponibilidade.quedas(tidy.getId(), 30).getFirst();

		assertThat(queda.emAndamento()).isFalse();
		assertThat(queda.fim()).isCloseTo(agora.minus(horas(4)), UM_SEGUNDO);
		assertThat(queda.duracaoSegundos()).isEqualTo(3600L);
	}

	@Test
	void ultimaVerificacaoDesempataPeloId() {
		Servico encore = servico("Encore", true);
		noAr(encore, horas(1), 100);
		fora(encore, horas(1), "gravada depois, no mesmo instante");

		assertThat(disponibilidade.resumo(encore.getId()).situacao()).isEqualTo(Situacao.FORA);
	}

	@Test
	void quedasDeUmServicoNaoMisturamComAsDeOutro() {
		Servico a = servico("A", true);
		Servico b = servico("B", true);
		fora(a, horas(3), "a caiu");
		noAr(b, horas(2), 100); // não pode terminar a queda do A
		noAr(a, horas(1), 100);

		Queda queda = disponibilidade.quedas(a.getId(), 30).getFirst();

		assertThat(queda.fim()).isCloseTo(agora.minus(horas(1)), UM_SEGUNDO);
		assertThat(disponibilidade.quedas(b.getId(), 30)).isEmpty();
	}

	@Test
	void limpezaApagaSoOHistoricoAntigo() {
		Servico coursebook = servico("Coursebook", true);
		noAr(coursebook, dias(91), 100);
		noAr(coursebook, dias(89), 100);

		assertThat(verificador.apagarAntigas()).isEqualTo(1);
		assertThat(verificacoes.count()).isEqualTo(1);
	}

	@Test
	void rotas() throws Exception {
		Servico encore = servico("Encore", true);
		noAr(encore, horas(1), 150);

		mvc.perform(get("/api/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nome").value("Encore"))
				.andExpect(jsonPath("$[0].situacao").value("NO_AR"))
				.andExpect(jsonPath("$[0].ultimas24h.disponibilidade").value(100.0));
		mvc.perform(get("/api/servicos/" + encore.getId() + "/resumo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ultimas24h.tempoMedioMs").value(150));
		mvc.perform(get("/api/servicos/" + encore.getId() + "/quedas"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
		mvc.perform(get("/api/servicos/999999/resumo")).andExpect(status().isNotFound());
		mvc.perform(get("/api/servicos/999999/quedas")).andExpect(status().isNotFound());
	}

}
