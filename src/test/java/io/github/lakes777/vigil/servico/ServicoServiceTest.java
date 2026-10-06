package io.github.lakes777.vigil.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.lakes777.vigil.seguranca.FiltroDeEnderecos;

/**
 * Testes de unidade: sem Spring e sem banco. O repositório é um objeto falso (Mockito),
 * entregue pelo construtor, do mesmo jeito que o Spring entrega o de verdade.
 */
class ServicoServiceTest {

	private ServicoRepository repositorio;
	private ServicoService service;

	@BeforeEach
	void preparar() {
		repositorio = mock(ServicoRepository.class);
		service = new ServicoService(repositorio, new FiltroDeEnderecos(false));
		when(repositorio.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
	}

	@Test
	void criarUsaOsPadroesETiraEspacosDoNome() {
		ServicoResposta criado = service.criar(new ServicoEntrada("  Encore ", "https://exemplo.com", null, null));

		assertThat(criado.nome()).isEqualTo("Encore");
		assertThat(criado.url()).isEqualTo("https://exemplo.com");
		assertThat(criado.intervaloSegundos()).isEqualTo(300);
		assertThat(criado.ativo()).isTrue();
	}

	@Test
	void criarRecusaEnderecoInternoSemSalvar() {
		for (String url : new String[] {"http://localhost:5432", "http://169.254.169.254/latest/meta-data/",
				"http://192.168.0.1/admin", "http://[::1]:8080/"}) {
			assertThatThrownBy(() -> service.criar(new ServicoEntrada("X", url, null, null)))
					.as(url)
					.isInstanceOf(UrlInvalidaException.class)
					.hasMessage("endereços internos (localhost, rede privada) não podem ser monitorados");
		}
		verify(repositorio, never()).save(any());
	}

	@Test
	void criarRecusaNomeEmUsoSemSalvar() {
		when(repositorio.existeComNome("Encore")).thenReturn(true);

		assertThatThrownBy(() -> service.criar(new ServicoEntrada("Encore", "https://exemplo.com", 60, true)))
				.isInstanceOf(NomeEmUsoException.class)
				.hasMessageContaining("Encore");
		verify(repositorio, never()).save(any());
	}

	@Test
	void atualizarServicoInexistenteDa404() {
		when(repositorio.findById(7L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.atualizar(7L, new ServicoEntrada("X", "https://x.com", null, null)))
				.isInstanceOf(ServicoNaoEncontradoException.class);
	}

	@Test
	void atualizarRecusaNomeDeOutroServico() {
		when(repositorio.findById(1L)).thenReturn(Optional.of(new Servico("Hanami", "https://a.com", 300, true)));
		when(repositorio.existeOutroComNome("Encore", 1L)).thenReturn(true);

		assertThatThrownBy(() -> service.atualizar(1L, new ServicoEntrada("Encore", "https://a.com", null, null)))
				.isInstanceOf(NomeEmUsoException.class);
	}

	@Test
	void atualizarMudaOsCampos() {
		Servico existente = new Servico("Hanami", "https://a.com", 300, true);
		when(repositorio.findById(1L)).thenReturn(Optional.of(existente));

		ServicoResposta atualizado = service.atualizar(1L, new ServicoEntrada("Hanami", "https://b.com", 120, false));

		assertThat(atualizado.url()).isEqualTo("https://b.com");
		assertThat(existente.getIntervaloSegundos()).isEqualTo(120);
		assertThat(existente.isAtivo()).isFalse();
	}

	@Test
	void atualizarSemOsOpcionaisMantemOsValoresAtuais() {
		Servico pausado = new Servico("Hanami", "https://a.com", 600, false);
		when(repositorio.findById(1L)).thenReturn(Optional.of(pausado));

		service.atualizar(1L, new ServicoEntrada("Hanami", "https://b.com", null, null));

		assertThat(pausado.getUrl()).isEqualTo("https://b.com");
		assertThat(pausado.getIntervaloSegundos()).isEqualTo(600);
		assertThat(pausado.isAtivo()).isFalse();
	}

	@Test
	void listarConverteParaResposta() {
		when(repositorio.findAllByOrderByNomeAsc()).thenReturn(List.of(new Servico("Tidy", "https://t.com", 300, true)));

		assertThat(service.listar()).extracting(ServicoResposta::nome).containsExactly("Tidy");
	}

	@Test
	void removerApagaOServicoEncontrado() {
		Servico existente = new Servico("Tidy", "https://t.com", 300, true);
		when(repositorio.findById(3L)).thenReturn(Optional.of(existente));

		service.remover(3L);

		verify(repositorio).delete(existente);
	}

	@Test
	void removerInexistenteDa404SemApagar() {
		when(repositorio.findById(3L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.remover(3L)).isInstanceOf(ServicoNaoEncontradoException.class);
		verify(repositorio, never()).delete(any());
	}

}
