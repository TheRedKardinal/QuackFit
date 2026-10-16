package it.quackfit.utente;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Streak dell'utente (1:1). La relazione è solo da questo lato, dove sta la chiave esterna:
 * Utente non ha un campo streak, che si legge con StreakRepository.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Streak {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** Giorni consecutivi, come salvati all'ultimo aggiornamento. */
	@Column(nullable = false)
	private int valore;

	/** Miglior streak di sempre. */
	@Column(nullable = false)
	private int record;

	/** Giorno (fuso Europe/Rome) dell'ultima lezione superata; null se non ce ne sono. */
	private LocalDate ultimaAttivita;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "utente_id", nullable = false, unique = true)
	private Utente utente;

	@Column(nullable = false)
	private Instant updatedAt;

	/** Streak iniziale di un nuovo utente: valore 0, record 0, nessuna attività. */
	public Streak(Utente utente, Instant adesso) {
		this.utente = utente;
		this.valore = 0;
		this.record = 0;
		this.updatedAt = adesso;
	}

}
