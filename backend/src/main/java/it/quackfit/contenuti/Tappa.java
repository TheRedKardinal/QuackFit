package it.quackfit.contenuti;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tappa del percorso. Soft delete: le tappe cancellate (deletedAt valorizzato) spariscono dalle query normali.
 *
 * Le cancellazioni le fanno i service, con deletedAt = Instant.now(clock) e la cascata su lezioni e domande.
 * @SQLDelete è solo una rete di sicurezza se qualcuno chiama repository.delete(): usa l'ora del database
 * e non fa nessuna cascata (vedi backend/CLAUDE.md, Soft delete).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE tappa SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Tappa {

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

	/** 1, 2, 3… senza buchi tra le tappe attive. Nessun vincolo UNIQUE: lo garantisce il service. */
	@Column(nullable = false)
	private int ordine;

	@Column(nullable = false)
	private Instant createdAt;

	/** null = attiva; valorizzato con il soft delete. */
	private Instant deletedAt;

	public Tappa(String titolo, String descrizione, int ordine, Instant adesso) {
		this.titolo = titolo;
		this.descrizione = descrizione;
		this.ordine = ordine;
		this.createdAt = adesso;
	}

}
