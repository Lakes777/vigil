package io.github.lakes777.vigil.servico;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um site ou API que o Vigil verifica de tempos em tempos (uma linha da tabela servico). */
@Entity
@Table(name = "servico")
public class Servico {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String nome;

	@Column(nullable = false, length = 500)
	private String url;

	@Column(name = "intervalo_segundos", nullable = false)
	private int intervaloSegundos;

	@Column(nullable = false)
	private boolean ativo;

	/** Posição na página: a menor primeiro (empate, pelo nome). */
	@Column(nullable = false)
	private int ordem;

	/** O que o "Abrir o site" abre; nulo = a própria URL verificada. */
	@Column(length = 500)
	private String link;

	@CreationTimestamp
	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@Column(name = "proxima_verificacao", nullable = false)
	private Instant proximaVerificacao;

	/** O JPA exige um construtor vazio para montar o objeto a partir do banco. */
	protected Servico() {
	}

	/** Sem ordem nem link (o começo da lista e o "Abrir o site" na própria URL). */
	public Servico(String nome, String url, int intervaloSegundos, boolean ativo) {
		this(nome, url, intervaloSegundos, ativo, 0, null);
	}

	public Servico(String nome, String url, int intervaloSegundos, boolean ativo, int ordem, String link) {
		alterar(nome, url, intervaloSegundos, ativo);
		mudarApresentacao(ordem, link);
	}

	/** A ordem e o link só mudam a página: não pedem uma verificação nova. */
	public void mudarApresentacao(int ordem, String link) {
		this.ordem = ordem;
		this.link = link;
	}

	public void alterar(String nome, String url, int intervaloSegundos, boolean ativo) {
		this.nome = nome;
		this.url = url;
		this.intervaloSegundos = intervaloSegundos;
		this.ativo = ativo;
		// Serviço novo ou editado (talvez com outra URL): verificar logo
		this.proximaVerificacao = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getUrl() {
		return url;
	}

	public int getIntervaloSegundos() {
		return intervaloSegundos;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public int getOrdem() {
		return ordem;
	}

	public String getLink() {
		return link;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getProximaVerificacao() {
		return proximaVerificacao;
	}

}
