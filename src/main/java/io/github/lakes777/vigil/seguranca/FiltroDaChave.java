package io.github.lakes777.vigil.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lê o cabeçalho "Authorization: Bearer <chave>". Chave certa: o pedido segue como admin.
 * Chave errada: 401 na hora. Sem o cabeçalho: segue como visitante, e quem decide se
 * visitante pode é a regra da SegurancaConfig (GET pode, o resto não).
 */
class FiltroDaChave extends OncePerRequestFilter {

	private static final String PREFIXO = "Bearer ";

	private final byte[] chave;

	FiltroDaChave(String chave) {
		this.chave = chave.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		String cabecalho = pedido.getHeader(HttpHeaders.AUTHORIZATION);
		if (cabecalho == null) {
			cadeia.doFilter(pedido, resposta);
			return;
		}
		// "Bearer" vale com qualquer caixa (RFC 7235); a chave, não
		boolean bearer = cabecalho.regionMatches(true, 0, PREFIXO, 0, PREFIXO.length());
		if (!bearer || !confere(cabecalho.substring(PREFIXO.length()).strip())) {
			Problema.escrever(resposta, HttpServletResponse.SC_UNAUTHORIZED, "Chave inválida",
					"A chave de admin enviada não confere.");
			return;
		}
		var admin = new UsernamePasswordAuthenticationToken("admin", null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
		SecurityContextHolder.getContext().setAuthentication(admin);
		try {
			cadeia.doFilter(pedido, resposta);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	/**
	 * MessageDigest.isEqual leva o mesmo tempo acerte ou erre o começo da chave.
	 * Um equals() comum para no primeiro caractere diferente, e medindo esse tempo
	 * daria para descobrir a chave aos poucos.
	 */
	private boolean confere(String recebida) {
		return MessageDigest.isEqual(chave, recebida.getBytes(StandardCharsets.UTF_8));
	}

}
