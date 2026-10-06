package io.github.lakes777.vigil.verificacao;

import java.time.Instant;

import io.github.lakes777.vigil.servico.Servico;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Uma verificação de um serviço (uma linha da tabela verificacao). */
@Entity
@Table(name = "verificacao")
public class Verificacao {

	static final int TAMANHO_DO_ERRO = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Muitas verificações para um serviço. LAZY: só busca o serviço no banco se alguém pedir. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "servico_id")
	private Servico servico;

	@Column(name = "feita_em", nullable = false)
	private Instant feitaEm;

	@Column(name = "no_ar", nullable = false)
	private boolean noAr;

	@Column(name = "codigo_http")
	private Integer codigoHttp;

	@Column(name = "tempo_ms", nullable = false)
	private int tempoMs;

	@Column(length = TAMANHO_DO_ERRO)
	private String erro;

	protected Verificacao() {
	}

	public Verificacao(Servico servico, Instant feitaEm, ResultadoSonda resultado) {
		this.servico = servico;
		this.feitaEm = feitaEm;
		this.noAr = resultado.noAr();
		this.codigoHttp = resultado.codigoHttp();
		this.tempoMs = resultado.tempoMs();
		String erro = resultado.erro();
		this.erro = erro != null && erro.length() > TAMANHO_DO_ERRO ? erro.substring(0, TAMANHO_DO_ERRO) : erro;
	}

	public Long getId() {
		return id;
	}

	public Instant getFeitaEm() {
		return feitaEm;
	}

	public boolean isNoAr() {
		return noAr;
	}

	public Integer getCodigoHttp() {
		return codigoHttp;
	}

	public int getTempoMs() {
		return tempoMs;
	}

	public String getErro() {
		return erro;
	}

}
