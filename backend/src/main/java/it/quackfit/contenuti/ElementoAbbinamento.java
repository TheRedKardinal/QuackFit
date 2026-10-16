package it.quackfit.contenuti;

import java.util.UUID;

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
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Elemento di una domanda ABBINAMENTO. Nessun soft delete, come Opzione. Si crea a coppie con Domanda.aggiungiCoppia.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ElementoAbbinamento {

	/** Lunghezza massima da admin.md. */
	public static final int MAX_TESTO = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = MAX_TESTO)
	private String testo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Lato lato;

	/**
	 * L'elemento DESTRA giusto: valorizzato solo sugli elementi SINISTRA, null sui DESTRA.
	 * UNIQUE: ogni DESTRA è l'abbinamento di un solo SINISTRA.
	 */
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "abbinamento_corretto_id", unique = true)
	private ElementoAbbinamento abbinamentoCorretto;

	/** Posizione della coppia nella domanda (1, 2, 3…), uguale per i due elementi della coppia. */
	@Column(nullable = false)
	private int ordine;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "domanda_id", nullable = false)
	private Domanda domanda;

	ElementoAbbinamento(Domanda domanda, String testo, Lato lato, ElementoAbbinamento abbinamentoCorretto, int ordine) {
		this.domanda = domanda;
		this.testo = testo;
		this.lato = lato;
		this.abbinamentoCorretto = abbinamentoCorretto;
		this.ordine = ordine;
	}

}
