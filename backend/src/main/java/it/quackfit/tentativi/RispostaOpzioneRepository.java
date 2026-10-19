package it.quackfit.tentativi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RispostaOpzioneRepository extends JpaRepository<RispostaOpzione, UUID> {

	/** La prima risposta a una domanda nel tentativo, se c'è (lavora con gli id, senza navigare verso la domanda). */
	Optional<RispostaOpzione> findByTentativoIdAndDomandaId(UUID tentativoId, UUID domandaId);

	/** Id delle domande attive già risolte nel tentativo (per la chiusura: le cancellate non contano). */
	@Query("select r.domanda.id from RispostaOpzione r where r.tentativo.id = :tentativoId and r.risolta = true")
	List<UUID> domandeRisolte(@Param("tentativoId") UUID tentativoId);

}
