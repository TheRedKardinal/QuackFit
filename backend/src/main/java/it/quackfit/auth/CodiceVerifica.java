package it.quackfit.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import it.quackfit.utente.Utente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Codice a 6 cifre inviato per email. Nel database c'è solo l'hash BCrypt:
 * il codice in chiaro esiste solo nell'email.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class CodiceVerifica {

	/** Validità di un codice dalla creazione. */
	public static final Duration VALIDITA = Duration.ofMinutes(15);

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoCodice tipo;

	@Column(nullable = false)
	private String codiceHash;

	@Column(nullable = false)
	private Instant scadenza;

	@Column(nullable = false)
	private int tentativiFalliti;

	/** null = ancora valido; valorizzato quando viene usato o invalidato da un codice più recente. */
	private Instant usatoAt;

	/** Solo per CAMBIO_EMAIL: l'indirizzo da confermare. */
	private String nuovaEmail;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "utente_id", nullable = false)
	private Utente utente;

	@Column(nullable = false)
	private Instant createdAt;

	public CodiceVerifica(Utente utente, TipoCodice tipo, String codiceHash, String nuovaEmail, Instant adesso) {
		this.utente = utente;
		this.tipo = tipo;
		this.codiceHash = codiceHash;
		this.nuovaEmail = nuovaEmail;
		this.tentativiFalliti = 0;
		this.createdAt = adesso;
		this.scadenza = adesso.plus(VALIDITA);
	}

}
