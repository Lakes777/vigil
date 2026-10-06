package io.github.lakes777.vigil.seguranca;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Proteção contra SSRF: o Vigil acessa a URL que alguém cadastrou, então sem este filtro
 * daria para usá-lo para enxergar o que só existe por dentro do servidor: o banco, outros
 * serviços da rede privada ou o 169.254.169.254, de onde as nuvens entregam as credenciais
 * da máquina.
 *
 * Bloqueia: localhost, redes privadas (10.x, 172.16-31.x, 192.168.x), link-local
 * (169.254.x, onde fica o endereço das nuvens), 100.64.x (rede interna de provedores),
 * multicast, reservados e os equivalentes em IPv6, inclusive IPv4 "escondido" dentro de um IPv6.
 */
@Component
public class FiltroDeEnderecos {

	private static final Pattern PARECE_IPV4 = Pattern.compile("^\\d+(\\.\\d+){0,3}$");

	private final boolean permitirLocalhost;

	/** permitirLocalhost só nos testes, em que os sites falsos (WireMock) rodam em 127.0.0.1. */
	public FiltroDeEnderecos(@Value("${vigil.verificacao.permitir-localhost:false}") boolean permitirLocalhost) {
		this.permitirLocalhost = permitirLocalhost;
	}

	/**
	 * Na hora de acessar: descobre os IPs do nome (DNS) e recusa se algum for interno.
	 * Um domínio pode apontar para vários IPs; basta um interno para recusar.
	 *
	 * O Java guarda a resposta do DNS por 30 s (fixado no main), e o HttpClient, logo em
	 * seguida, quase sempre usa essa mesma resposta. Isso reduz muito a chance de um domínio
	 * malicioso responder "IP público" para este filtro e "127.0.0.1" para a conexão (o ataque
	 * chamado DNS rebinding). Sobra uma janela pequena: o cache vencer bem entre os dois.
	 * Fechar de vez exigiria conectar direto no IP conferido, o que o HttpClient não permite.
	 */
	public void conferir(URI endereco) throws UnknownHostException {
		String host = endereco.getHost();
		if (host == null) {
			throw new IllegalArgumentException("URL sem endereço de site");
		}
		for (InetAddress ip : InetAddress.getAllByName(host)) {
			if (bloqueado(ip)) {
				throw new EnderecoBloqueadoException();
			}
		}
	}

	/**
	 * No cadastro, sem consultar o DNS (um site recém-criado pode nem ter DNS ainda):
	 * recusa só o que já se vê na própria URL, como localhost ou um IP interno digitado.
	 * A proteção de verdade é o conferir(), na hora de cada acesso.
	 */
	public boolean bloqueadoNoCadastro(String host) {
		String nome = host.toLowerCase(Locale.ROOT);
		if (nome.endsWith(".")) {
			nome = nome.substring(0, nome.length() - 1);
		}
		if (nome.equals("localhost") || nome.endsWith(".localhost")) {
			return !permitirLocalhost;
		}
		if (!PARECE_IPV4.matcher(nome).matches() && !nome.startsWith("[")) {
			return false;
		}
		try {
			// Com um IP no texto, o Java só converte, sem consultar o DNS
			return bloqueado(InetAddress.getByName(nome));
		} catch (UnknownHostException erro) {
			return false;
		}
	}

	boolean bloqueado(InetAddress ip) {
		if (ip.isLoopbackAddress()) {
			return !permitirLocalhost;
		}
		if (ip.isAnyLocalAddress() || ip.isLinkLocalAddress() || ip.isSiteLocalAddress() || ip.isMulticastAddress()) {
			return true;
		}
		byte[] b = ip.getAddress();
		if (ip instanceof Inet4Address) {
			return ipv4Reservado(b);
		}
		// IPv6 privado (fc00::/7, o equivalente ao 10.x)
		if ((b[0] & 0xfe) == 0xfc) {
			return true;
		}
		byte[] ipv4 = ipv4Embutido(b);
		if (ipv4 != null) {
			try {
				return bloqueado(InetAddress.getByAddress(ipv4));
			} catch (UnknownHostException impossivel) {
				return true;
			}
		}
		return false;
	}

	/** O que o Java não marca sozinho: 0.x, 100.64-127.x, 198.18-19.x e 240.x em diante (inclui 255.255.255.255). */
	private static boolean ipv4Reservado(byte[] b) {
		int primeiro = b[0] & 0xff;
		int segundo = b[1] & 0xff;
		return primeiro == 0
				|| (primeiro == 100 && segundo >= 64 && segundo <= 127)
				|| (primeiro == 198 && (segundo == 18 || segundo == 19))
				|| primeiro >= 240;
	}

	/**
	 * Formas de levar um IPv4 dentro de um IPv6, que acabam chegando nele: ::a.b.c.d,
	 * 64:ff9b::a.b.c.d (NAT64) e 2002:aabb:ccdd:: (6to4). O ::ffff:a.b.c.d o Java já
	 * converte sozinho para IPv4.
	 */
	private static byte[] ipv4Embutido(byte[] b) {
		if (zeros(b, 0, 12)) {
			return Arrays.copyOfRange(b, 12, 16);
		}
		if (b[0] == 0x00 && b[1] == 0x64 && b[2] == (byte) 0xff && b[3] == (byte) 0x9b && zeros(b, 4, 12)) {
			return Arrays.copyOfRange(b, 12, 16);
		}
		if (b[0] == 0x20 && b[1] == 0x02) {
			return Arrays.copyOfRange(b, 2, 6);
		}
		return null;
	}

	private static boolean zeros(byte[] b, int de, int ate) {
		for (int i = de; i < ate; i++) {
			if (b[i] != 0) {
				return false;
			}
		}
		return true;
	}

}
