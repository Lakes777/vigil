package io.github.lakes777.vigil.servico;

import static io.github.lakes777.vigil.Admin.comChave;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.lakes777.vigil.seguranca.SegurancaConfig;

/**
 * Só a camada web (sem banco): o ServicoService é falso, para simular erros difíceis
 * de provocar de verdade, como dois pedidos ao mesmo tempo. O @WebMvcTest não carrega
 * as classes @Configuration, então as regras de segurança entram pelo @Import.
 */
@WebMvcTest(ServicoController.class)
@Import(SegurancaConfig.class)
class ServicoControllerErrosTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ServicoService servicos;

	@Value("${vigil.admin.chave}")
	private String chave;

	@Test
	void indiceDoBancoBarrandoNomeViraConflito() throws Exception {
		when(servicos.criar(any())).thenThrow(new DataIntegrityViolationException("servico_nome_unico"));

		mvc.perform(post("/api/servicos").with(comChave(chave)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Encore\",\"url\":\"https://x.com\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Conflito"));
	}

	@Test
	void removidoPorOutroPedidoViraConflito() throws Exception {
		doThrow(new ObjectOptimisticLockingFailureException(Servico.class, 1L)).when(servicos).remover(anyLong());

		mvc.perform(delete("/api/servicos/1").with(comChave(chave)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("O serviço foi alterado ou removido por outro pedido. Tente de novo."));
	}

}
