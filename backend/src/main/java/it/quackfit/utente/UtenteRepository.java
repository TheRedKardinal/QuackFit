package it.quackfit.utente;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UtenteRepository extends JpaRepository<Utente, UUID> {

	/** Cerca solo tra gli utenti attivi (deletedAt null). */
	Optional<Utente> findByEmailAndDeletedAtIsNull(String email);

	/**
	 * Controlla se l'email è già usata. Non serve filtrare per deletedAt:
	 * l'anonimizzazione sostituisce l'email, che torna così libera.
	 */
	boolean existsByEmail(String email);

}
