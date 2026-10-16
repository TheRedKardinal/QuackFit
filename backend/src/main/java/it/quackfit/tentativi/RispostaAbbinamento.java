package it.quackfit.tentativi;

import java.util.UUID;

import it.quackfit.contenuti.Domanda;
import it.quackfit.contenuti.ElementoAbbinamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una coppia della prima risposta a una domanda ABBINAMENTO in un tentativo (una riga per coppia), per il resoconto.
 *
 * Si salva solo la prima risposta, e in una risposta valida ogni elemento di sinistra e di destra compare una volta
 * sola: per questo sono UNIQUE sia tentativo + sinistro sia tentativo + destro. È l'ultima difesa contro due prime
 * risposte salvate in parallelo; la prima è il lock pessimistico sul tentativo nel service.
 *
 * Dalla risposta non si naviga verso domanda ed elementi (possono essere cancellati): si usano query con gli id
 * o le query di ResocontoQuery.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(uniqueConstraints = {
		@UniqueConstraint(name = "uk_risposta_abbinamento_tentativo_sinistro",
				columnNames = { "tentativo_id", "elemento_sinistro_id" }),
		@UniqueConstraint(name = "uk_risposta_abbinamento_tentativo_destro",
				columnNames = { "tentativo_id", "elemento_destro_id" }) })
public class RispostaAbbinamento {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tentativo_id", nullable = false)
	private TentativoLezione tentativo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "domanda_id", nullable = false)
	private Domanda domanda;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "elemento_sinistro_id", nullable = false)
	private ElementoAbbinamento elementoSinistro;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "elemento_destro_id", nullable = false)
	private ElementoAbbinamento elementoDestro;

	/** Come in RispostaOpzione; lo stesso valore su tutte le righe della stessa domanda. */
	@Column(nullable = false)
	private boolean risolta;

	public RispostaAbbinamento(TentativoLezione tentativo, Domanda domanda, ElementoAbbinamento elementoSinistro,
			ElementoAbbinamento elementoDestro, boolean risolta) {
		this.tentativo = tentativo;
		this.domanda = domanda;
		this.elementoSinistro = elementoSinistro;
		this.elementoDestro = elementoDestro;
		this.risolta = risolta;
	}

}
