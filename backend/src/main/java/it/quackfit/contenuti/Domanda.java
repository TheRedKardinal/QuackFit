package it.quackfit.contenuti;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domanda di una lezione. Soft delete come Tappa: le cancellazioni le fanno i service, @SQLDelete è solo una rete
 * di sicurezza senza cascata.
 *
 * Opzioni ed elementi sono parti della domanda: si creano e si salvano con lei (cascade PERSIST e MERGE).
 * - Niente cascade REMOVE: la domanda ha il soft delete, e opzioni ed elementi devono restare nel database
 *   perché il resoconto dei tentativi li mostra anche dopo la cancellazione della domanda.
 * - Niente orphanRemoval: opzioni ed elementi non si tolgono mai (la modifica cambia solo i testi, la struttura
 *   si cambia cancellando e ricreando la domanda, vedi admin.md); togliere un elemento dalla lista non deve
 *   cancellare righe a cui puntano le risposte salvate.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE domanda SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Domanda {

	/** Lunghezze massime da admin.md. */
	public static final int MAX_TESTO = 500;
	public static final int MAX_SPIEGAZIONE = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = MAX_TESTO)
	private String testo;

	/** Mostrata solo dopo la risposta. */
	@Column(nullable = false, length = MAX_SPIEGAZIONE)
	private String spiegazione;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoDomanda tipo;

	/**
	 * 1, 2, 3… senza buchi tra le domande attive della lezione, assegnato in creazione (le domande non si
	 * riordinano). Nessun vincolo UNIQUE: lo garantisce il service.
	 */
	@Column(nullable = false)
	private int ordine;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lezione_id", nullable = false)
	private Lezione lezione;

	@Column(nullable = false)
	private Instant createdAt;

	/** null = attiva; valorizzato con il soft delete. */
	private Instant deletedAt;

	/** Per VERO_FALSO e SCELTA_MULTIPLA. Si aggiungono solo con {@link #aggiungiOpzione}. */
	@OneToMany(mappedBy = "domanda", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
	@OrderBy("ordine")
	@Setter(AccessLevel.NONE)
	private List<Opzione> opzioni = new ArrayList<>();

	/** Per ABBINAMENTO. Si aggiungono solo con {@link #aggiungiCoppia}. */
	@OneToMany(mappedBy = "domanda", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
	@OrderBy("lato, ordine")
	@Setter(AccessLevel.NONE)
	private List<ElementoAbbinamento> elementi = new ArrayList<>();

	public Domanda(Lezione lezione, String testo, String spiegazione, TipoDomanda tipo, int ordine, Instant adesso) {
		this.lezione = lezione;
		this.testo = testo;
		this.spiegazione = spiegazione;
		this.tipo = tipo;
		this.ordine = ordine;
		this.createdAt = adesso;
	}

	/** Aggiunge un'opzione in fondo (ordine 1, 2, 3…). */
	public Opzione aggiungiOpzione(String testo, boolean corretta) {
		Opzione opzione = new Opzione(this, testo, corretta, opzioni.size() + 1);
		opzioni.add(opzione);
		return opzione;
	}

	/**
	 * Aggiunge una coppia: l'elemento SINISTRA punta al DESTRA giusto. I due elementi hanno lo stesso ordine,
	 * la posizione della coppia (1, 2, 3…).
	 *
	 * Il DESTRA entra nella lista prima del SINISTRA: la cascata PERSIST segue l'ordine della lista e Hibernate
	 * esegue le INSERT nello stesso ordine, quindi quando si inserisce il SINISTRA la riga a cui punta
	 * (abbinamento_corretto_id) esiste già.
	 */
	public void aggiungiCoppia(String sinistra, String destra) {
		int ordine = elementi.size() / 2 + 1;
		ElementoAbbinamento destro = new ElementoAbbinamento(this, destra, Lato.DESTRA, null, ordine);
		ElementoAbbinamento sinistro = new ElementoAbbinamento(this, sinistra, Lato.SINISTRA, destro, ordine);
		elementi.add(destro);
		elementi.add(sinistro);
	}

}
