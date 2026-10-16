package it.quackfit.tentativi;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RispostaAbbinamentoRepository extends JpaRepository<RispostaAbbinamento, UUID> {

	/** Le coppie della prima risposta a una domanda nel tentativo (vuota se non ha ancora risposto). */
	List<RispostaAbbinamento> findByTentativoIdAndDomandaId(UUID tentativoId, UUID domandaId);

}
