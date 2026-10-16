package it.quackfit.utente;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Utente registrato. Niente @SQLRestriction: gli utenti cancellati (deletedAt valorizzato)
 * restano leggibili per lo storico dei tentativi; i controlli su deletedAt sono espliciti.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Utente {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String nome;

	@Column(nullable = false)
	private String cognome;

	@Column(nullable = false, unique = true)
	private String email;

	/** Hash BCrypt, mai la password in chiaro. */
	@Column(nullable = false)
	private String password;

	/** URL Cloudinary; null se l'utente non ha una foto. */
	private String fotoProfilo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Ruolo ruolo;

	@Column(nullable = false)
	private boolean emailVerificata;

	/** I token emessi prima di questa data vengono rifiutati ("logout ovunque"). */
	@Column(nullable = false)
	private Instant tokenValidiDal;

	@Column(nullable = false)
	private int loginFalliti;

	/** Inizio della serie di login falliti; null se non ce ne sono. */
	private Instant primoLoginFallitoAt;

	/** Account bloccato fino a questa data; null se non è bloccato. */
	private Instant bloccatoFino;

	@Column(nullable = false)
	private Instant createdAt;

	/** null = account attivo; valorizzato con l'anonimizzazione. */
	private Instant deletedAt;

	/**
	 * Nuovo utente appena registrato, con i valori iniziali di auth.md.
	 * {@code adesso} arriva dal Clock del service, così nei test l'orario è controllabile.
	 */
	public Utente(String nome, String cognome, String email, String password, Instant adesso) {
		this.nome = nome;
		this.cognome = cognome;
		this.email = email;
		this.password = password;
		this.ruolo = Ruolo.UTENTE;
		this.emailVerificata = false;
		this.tokenValidiDal = adesso;
		this.loginFalliti = 0;
		this.createdAt = adesso;
	}

}
