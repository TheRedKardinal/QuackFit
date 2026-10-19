package it.quackfit.tentativi;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RispostaAbbinamentoRepository extends JpaRepository<RispostaAbbinamento, UUID> {

	/** Le coppie della prima risposta a una domanda nel tentativo (vuota se non ha ancora risposto). */
	List<RispostaAbbinamento> findByTentativoIdAndDomandaId(UUID tentativoId, UUID domandaId);

	/**
	 * Id delle domande attive già risolte nel tentativo (per la chiusura). {@code risolta} è uguale su tutte le coppie
	 * di una domanda, da qui il distinct.
	 */
	@Query("""
			select distinct r.domanda.id from RispostaAbbinamento r
			where r.tentativo.id = :tentativoId and r.risolta = true""")
	List<UUID> domandeRisolte(@Param("tentativoId") UUID tentativoId);

}
