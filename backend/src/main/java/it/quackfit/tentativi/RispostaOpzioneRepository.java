package it.quackfit.tentativi;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RispostaOpzioneRepository extends JpaRepository<RispostaOpzione, UUID> {

	/** La prima risposta a una domanda nel tentativo, se c'è (lavora con gli id, senza navigare verso la domanda). */
	Optional<RispostaOpzione> findByTentativoIdAndDomandaId(UUID tentativoId, UUID domandaId);

}
