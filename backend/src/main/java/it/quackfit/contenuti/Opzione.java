package it.quackfit.contenuti;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Opzione di una domanda VERO_FALSO o SCELTA_MULTIPLA. Nessun soft delete: resta finché esiste la domanda,
 * anche se cancellata, così il resoconto dei tentativi la mostra ancora. Si crea con Domanda.aggiungiOpzione.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Opzione {

	/** Lunghezza massima da admin.md. */
	public static final int MAX_TESTO = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = MAX_TESTO)
	private String testo;

	/** Una sola opzione corretta per domanda: lo garantisce il service. */
	@Column(nullable = false)
	private boolean corretta;

	@Column(nullable = false)
	private int ordine;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "domanda_id", nullable = false)
	private Domanda domanda;

	Opzione(Domanda domanda, String testo, boolean corretta, int ordine) {
		this.domanda = domanda;
		this.testo = testo;
		this.corretta = corretta;
		this.ordine = ordine;
	}

}
