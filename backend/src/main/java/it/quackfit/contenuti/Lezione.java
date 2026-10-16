package it.quackfit.contenuti;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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
 * Lezione di una tappa. Soft delete come Tappa: le cancellazioni le fanno i service, @SQLDelete è solo una rete
 * di sicurezza senza cascata. Le domande si leggono da DomandaRepository (in ordine e solo quelle attive).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE lezione SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Lezione {

	/** Lunghezze massime da admin.md. */
	public static final int MAX_TITOLO = 100;
	public static final int MAX_DESCRIZIONE = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = MAX_TITOLO)
	private String titolo;

	@Column(nullable = false, length = MAX_DESCRIZIONE)
	private String descrizione;

	/** 1, 2, 3… senza buchi tra le lezioni attive della tappa. Nessun vincolo UNIQUE: lo garantisce il service. */
	@Column(nullable = false)
	private int ordine;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tappa_id", nullable = false)
	private Tappa tappa;

	@Column(nullable = false)
	private Instant createdAt;

	/** null = attiva; valorizzato con il soft delete. */
	private Instant deletedAt;

	public Lezione(Tappa tappa, String titolo, String descrizione, int ordine, Instant adesso) {
		this.tappa = tappa;
		this.titolo = titolo;
		this.descrizione = descrizione;
		this.ordine = ordine;
		this.createdAt = adesso;
	}

}
