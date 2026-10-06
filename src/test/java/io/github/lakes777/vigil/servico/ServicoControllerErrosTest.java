package io.github.lakes777.vigil.servico;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Só a camada web (sem banco): o ServicoService é falso, para simular erros difíceis
 * de provocar de verdade, como dois pedidos ao mesmo tempo.
 */
@WebMvcTest(ServicoController.class)
class ServicoControllerErrosTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ServicoService servicos;

	@Test
	void indiceDoBancoBarrandoNomeViraConflito() throws Exception {
		when(servicos.criar(any())).thenThrow(new DataIntegrityViolationException("servico_nome_unico"));

		mvc.perform(post("/api/servicos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Encore\",\"url\":\"https://x.com\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Conflito"));
	}

	@Test
	void removidoPorOutroPedidoViraConflito() throws Exception {
		doThrow(new ObjectOptimisticLockingFailureException(Servico.class, 1L)).when(servicos).remover(anyLong());

		mvc.perform(delete("/api/servicos/1"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("O serviço foi alterado ou removido por outro pedido. Tente de novo."));
	}

}
