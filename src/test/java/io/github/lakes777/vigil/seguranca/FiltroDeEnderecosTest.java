package io.github.lakes777.vigil.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Só IPs escritos direto: o Java converte o texto sem consultar o DNS, então roda sem internet. */
class FiltroDeEnderecosTest {

	private final FiltroDeEnderecos filtro = new FiltroDeEnderecos(false);

	@ParameterizedTest
	@ValueSource(strings = {
			"127.0.0.1", "127.8.9.10", "0.0.0.0", "0.1.2.3", // localhost e "esta rede"
			"10.0.0.5", "172.16.0.1", "172.31.255.255", "192.168.1.1", // redes privadas
			"169.254.169.254", // credenciais das nuvens (AWS, Oracle, Google...)
			"100.64.0.1", "100.127.255.255", // rede interna de provedores
			"198.18.0.1", "224.0.0.1", "240.0.0.1", "255.255.255.255", // testes, multicast, reservados
			"::1", "::", "fe80::1", "fd00::1", "fc00::abcd", "ff02::1", // os mesmos em IPv6
			"::ffff:127.0.0.1", "::ffff:169.254.169.254", // IPv4 dentro de IPv6
			"::10.0.0.1", "64:ff9b::a9fe:a9fe", "2002:a9fe:a9fe::1"})
	void bloqueiaEnderecosInternos(String ip) throws UnknownHostException {
		assertThat(filtro.bloqueado(InetAddress.getByName(ip))).as(ip).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"8.8.8.8", "1.1.1.1", "172.32.0.1", "100.128.0.1", "2606:4700:4700::1111",
			"64:ff9b::808:808"})
	void liberaEnderecosPublicos(String ip) throws UnknownHostException {
		assertThat(filtro.bloqueado(InetAddress.getByName(ip))).as(ip).isFalse();
	}

	@Test
	void localhostSoComAOpcaoDosTestes() throws UnknownHostException {
		FiltroDeEnderecos dosTestes = new FiltroDeEnderecos(true);

		assertThat(dosTestes.bloqueado(InetAddress.getByName("127.0.0.1"))).isFalse();
		assertThat(dosTestes.bloqueado(InetAddress.getByName("::1"))).isFalse();
		// A rede privada continua bloqueada
		assertThat(dosTestes.bloqueado(InetAddress.getByName("169.254.169.254"))).isTrue();
	}

	@Test
	void conferirLancaParaEnderecoInterno() {
		assertThatThrownBy(() -> filtro.conferir(URI.create("http://169.254.169.254/latest/meta-data/")))
				.isInstanceOf(EnderecoBloqueadoException.class);
		assertThatThrownBy(() -> filtro.conferir(URI.create("http://[::ffff:7f00:1]:8080/")))
				.isInstanceOf(EnderecoBloqueadoException.class);
		assertThatCode(() -> filtro.conferir(URI.create("https://8.8.8.8/"))).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = {"localhost", "LocalHost", "localhost.", "api.localhost", "127.0.0.1", "127.1",
			"2130706433", "10.0.0.5", "169.254.169.254", "[::1]", "[::ffff:169.254.169.254]", "[fd00::1]"})
	void cadastroRecusaOQueSeVeNaUrl(String host) {
		assertThat(filtro.bloqueadoNoCadastro(host)).as(host).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"exemplo.com", "painel-estudos-cyan.vercel.app", "8.8.8.8", "[2606:4700:4700::1111]",
			"localhost.exemplo.com", "10.exemplo.com"})
	void cadastroAceitaEnderecosPublicos(String host) {
		assertThat(filtro.bloqueadoNoCadastro(host)).as(host).isFalse();
	}

}
