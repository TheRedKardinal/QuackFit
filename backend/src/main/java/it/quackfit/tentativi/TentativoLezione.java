package it.quackfit.tentativi;

import java.time.Instant;
import java.util.UUID;

import it.quackfit.contenuti.Lezione;
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
 * Un tentativo di una lezione da parte di un utente (vedi tentativi.md).
 *
 * Attenzione: la lezione può essere cancellata (soft delete) dopo il tentativo. getLezione() su una lezione
 * cancellata lancia EntityNotFoundException, perché @SQLRestriction vale anche per le relazioni: dal tentativo
 * non si naviga verso la lezione, si usano query con gli id o proiezioni (vedi backend/CLAUDE.md, Soft delete).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class TentativoLezione {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private Instant iniziatoAt;

	/** null = tentativo in corso. */
	private Instant finitoAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EsitoTentativo esito;

	/** Al MAX_ERRORI-esimo errore il tentativo diventa FALLITO; non supera mai MAX_ERRORI. */
	@Column(nullable = false)
	private int errori;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "utente_id", nullable = false)
	private Utente utente;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lezione_id", nullable = false)
	private Lezione lezione;

	/** Nuovo tentativo: IN_CORSO, zero errori, iniziato adesso (dal Clock del service). */
	public TentativoLezione(Utente utente, Lezione lezione, Instant adesso) {
		this.utente = utente;
		this.lezione = lezione;
		this.iniziatoAt = adesso;
		this.esito = EsitoTentativo.IN_CORSO;
		this.errori = 0;
	}

}
