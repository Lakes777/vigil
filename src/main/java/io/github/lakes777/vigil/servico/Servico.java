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

	@CreationTimestamp
	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	/** O JPA exige um construtor vazio para montar o objeto a partir do banco. */
	protected Servico() {
	}

	public Servico(String nome, String url, int intervaloSegundos, boolean ativo) {
		alterar(nome, url, intervaloSegundos, ativo);
	}

	public void alterar(String nome, String url, int intervaloSegundos, boolean ativo) {
		this.nome = nome;
		this.url = url;
		this.intervaloSegundos = intervaloSegundos;
		this.ativo = ativo;
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

	public Instant getCriadoEm() {
		return criadoEm;
	}

}
