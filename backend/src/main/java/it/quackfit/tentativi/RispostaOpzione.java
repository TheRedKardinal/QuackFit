package it.quackfit.tentativi;

import java.util.UUID;

import it.quackfit.contenuti.Domanda;
import it.quackfit.contenuti.Opzione;
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
 * Prima risposta a una domanda VERO_FALSO o SCELTA_MULTIPLA in un tentativo, per il resoconto.
 * UNIQUE su tentativo + domanda: si salva solo la prima risposta; le successive cambiano solo {@code risolta}.
 *
 * Dalla risposta non si naviga verso domanda e opzione (possono essere cancellate): si usano query con gli id
 * o le query di ResocontoQuery.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_risposta_opzione_tentativo_domanda",
		columnNames = { "tentativo_id", "domanda_id" }))
public class RispostaOpzione {

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
	@JoinColumn(name = "opzione_id", nullable = false)
	private Opzione opzione;

	/** true se la prima risposta era giusta, oppure se la domanda è stata poi ripetuta correttamente. */
	@Column(nullable = false)
	private boolean risolta;

	public RispostaOpzione(TentativoLezione tentativo, Domanda domanda, Opzione opzione, boolean risolta) {
		this.tentativo = tentativo;
		this.domanda = domanda;
		this.opzione = opzione;
		this.risolta = risolta;
	}

}
