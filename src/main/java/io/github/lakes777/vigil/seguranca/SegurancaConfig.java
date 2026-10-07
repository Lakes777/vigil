package io.github.lakes777.vigil.seguranca;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Quem pode o quê: ler (GET) é público, porque a página de status vai mostrar tudo isso.
 * Mudar algo (POST, PUT, DELETE, inclusive o "verificar agora") só com a chave de admin.
 *
 * Sem sessão nem cookie: a chave vai em todo pedido. Por isso a proteção contra CSRF,
 * que existe para cookies mandados sozinhos pelo navegador, não se aplica e fica desligada.
 *
 * CORS: outro site (o portfólio) pode ler a API pelo navegador, mas só com GET e só as
 * origens de vigil.cors.origens. Alterar continua sendo só para quem tem a chave.
 */
@Configuration
public class SegurancaConfig {

	private static final Logger log = LoggerFactory.getLogger(SegurancaConfig.class);

	static final int TAMANHO_MINIMO = 32;

	private final String chave;

	public SegurancaConfig(@Value("${vigil.admin.chave:}") String chave) {
		// Melhor a API nem subir do que subir sem senha ou com uma fácil de adivinhar
		if (chave.strip().length() < TAMANHO_MINIMO) {
			throw new IllegalStateException("Defina vigil.admin.chave (variável VIGIL_ADMIN_CHAVE) com pelo menos "
					+ TAMANHO_MINIMO + " caracteres. Para gerar uma: openssl rand -hex 32");
		}
		this.chave = chave.strip();
	}

	@Bean
	SecurityFilterChain regras(HttpSecurity http) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				// Usa o bean corsConfigurationSource abaixo (o Spring Security procura por esse nome)
				.cors(cors -> {})
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(new FiltroDaChave(chave), AnonymousAuthenticationFilter.class)
				.authorizeHttpRequests(regra -> regra
						.requestMatchers(HttpMethod.GET, "/**").permitAll()
						.requestMatchers(HttpMethod.HEAD, "/**").permitAll()
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						// Páginas de erro do próprio Spring (ex.: 404 de um POST numa rota que não existe)
						.requestMatchers("/error").permitAll()
						.anyRequest().hasRole("ADMIN"))
				.exceptionHandling(erros -> erros.authenticationEntryPoint((pedido, resposta, erro) ->
						Problema.escrever(resposta, HttpServletResponse.SC_UNAUTHORIZED, "Chave de admin necessária",
								"Envie o cabeçalho Authorization: Bearer <chave> para alterar dados.")))
				.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${vigil.cors.origens:}") List<String> origens) {
		CorsConfiguration regra = new CorsConfiguration();
		List<String> permitidas = origens.stream().map(String::strip).filter(o -> !o.isEmpty()).toList();
		if (permitidas.isEmpty()) {
			// Não é falha de segurança (fecha tudo), mas os selos do portfólio somem sem aviso
			log.warn("Nenhuma origem em vigil.cors.origens: nenhum site consegue ler a API pelo navegador");
		}
		regra.setAllowedOrigins(permitidas);
		regra.setAllowedMethods(List.of("GET"));
		regra.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
		fonte.registerCorsConfiguration("/api/**", regra);
		return fonte;
	}

	/**
	 * Não há login com usuário e senha: só a chave. Sem este bean, o Spring Security
	 * criaria sozinho um usuário "user" com uma senha aleatória escrita no log.
	 */
	@Bean
	AuthenticationManager semLoginPorSenha() {
		return autenticacao -> {
			throw new ProviderNotFoundException("O Vigil só aceita a chave de admin");
		};
	}

}
