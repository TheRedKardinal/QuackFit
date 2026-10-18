package it.quackfit.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.quackfit.utente.Utente;

public interface CodiceVerificaRepository extends JpaRepository<CodiceVerifica, UUID> {

	/** L'ultimo codice ancora valido (usatoAt null): è quello da verificare. */
	Optional<CodiceVerifica> findFirstByUtenteAndTipoAndUsatoAtIsNullOrderByCreatedAtDesc(Utente utente, TipoCodice tipo);

	/** L'ultimo codice creato, usato o no: serve per il limite dei 60 secondi tra due invii. */
	Optional<CodiceVerifica> findFirstByUtenteAndTipoOrderByCreatedAtDesc(Utente utente, TipoCodice tipo);

	/** I codici ancora validi, da invalidare quando se ne genera uno nuovo. */
	List<CodiceVerifica> findByUtenteAndTipoAndUsatoAtIsNull(Utente utente, TipoCodice tipo);

	/** Cancellazione dell'account: i codici dell'utente si eliminano definitivamente (vedi me.md). */
	void deleteByUtente(Utente utente);

}
