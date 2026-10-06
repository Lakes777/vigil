package io.github.lakes777.vigil.erro;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.lakes777.vigil.servico.NomeEmUsoException;
import io.github.lakes777.vigil.servico.ServicoNaoEncontradoException;
import io.github.lakes777.vigil.servico.UrlInvalidaException;
import io.github.lakes777.vigil.verificacao.MuitasVerificacoesException;

/**
 * Transforma exceções em respostas HTTP no formato padrão "problem detail" (RFC 9457):
 * {"status": 404, "title": "...", "detail": "..."}. Os controllers só lançam a exceção.
 */
@RestControllerAdvice
public class TratadorDeErros extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ServicoNaoEncontradoException.class)
	ProblemDetail naoEncontrado(ServicoNaoEncontradoException erro) {
		return problema(HttpStatus.NOT_FOUND, "Serviço não encontrado", erro.getMessage());
	}

	@ExceptionHandler(NomeEmUsoException.class)
	ProblemDetail nomeEmUso(NomeEmUsoException erro) {
		return problema(HttpStatus.CONFLICT, "Nome em uso", erro.getMessage());
	}

	/** Mesmo formato da validação das anotações, para quem usa a API tratar igual. */
	@ExceptionHandler(UrlInvalidaException.class)
	ProblemDetail urlInvalida(UrlInvalidaException erro) {
		ProblemDetail corpo = problema(HttpStatus.BAD_REQUEST, "Dados inválidos", "Confira os campos indicados.");
		corpo.setProperty("campos", Map.of("url", erro.getMessage()));
		return corpo;
	}

	/** Dois pedidos com o mesmo nome ao mesmo tempo: a checagem passa nos dois, o índice do banco barra um. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail conflitoNoBanco(DataIntegrityViolationException erro) {
		return problema(HttpStatus.CONFLICT, "Conflito", "Os dados conflitam com um registro existente.");
	}

	/** Dois pedidos mexendo no mesmo serviço ao mesmo tempo, e um deles o apagou antes. */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail alteradoAoMesmoTempo(ObjectOptimisticLockingFailureException erro) {
		return problema(HttpStatus.CONFLICT, "Conflito", "O serviço foi alterado ou removido por outro pedido. Tente de novo.");
	}

	/** 429 com Retry-After: o cabeçalho padrão que diz em quantos segundos tentar de novo. */
	@ExceptionHandler(MuitasVerificacoesException.class)
	ResponseEntity<ProblemDetail> muitasVerificacoes(MuitasVerificacoesException erro) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(erro.getSegundosRestantes()))
				.body(problema(HttpStatus.TOO_MANY_REQUESTS, "Muitas verificações", erro.getMessage()));
	}

	/** Ex.: GET /api/servicos/abc, quando o id precisa ser número. */
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException erro, HttpHeaders headers,
			HttpStatusCode status, WebRequest pedido) {
		ProblemDetail corpo = problema(HttpStatus.BAD_REQUEST, "Parâmetro inválido",
				"O valor \"" + erro.getValue() + "\" não serve para " + erro.getPropertyName() + ".");
		return handleExceptionInternal(erro, corpo, headers, status, pedido);
	}

	/** Falha nas anotações do ServicoEntrada: devolve a mensagem de cada campo em "campos". */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException erro,
			HttpHeaders headers, HttpStatusCode status, WebRequest pedido) {
		Map<String, String> campos = new TreeMap<>();
		erro.getBindingResult().getFieldErrors()
				.forEach(campo -> campos.putIfAbsent(campo.getField(), campo.getDefaultMessage()));
		ProblemDetail corpo = problema(HttpStatus.BAD_REQUEST, "Dados inválidos", "Confira os campos indicados.");
		corpo.setProperty("campos", campos);
		return handleExceptionInternal(erro, corpo, headers, status, pedido);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException erro,
			HttpHeaders headers, HttpStatusCode status, WebRequest pedido) {
		ProblemDetail corpo = problema(HttpStatus.BAD_REQUEST, "JSON inválido",
				"O corpo do pedido não é um JSON válido ou tem um campo com o tipo errado.");
		return handleExceptionInternal(erro, corpo, headers, status, pedido);
	}

	private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
		ProblemDetail corpo = ProblemDetail.forStatusAndDetail(status, detalhe);
		corpo.setTitle(titulo);
		return corpo;
	}

}
