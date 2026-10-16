package it.quackfit.utente;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface UtenteRepository extends JpaRepository<Utente, UUID> {

	/** Cerca solo tra gli utenti attivi (deletedAt null). */
	Optional<Utente> findByEmailAndDeletedAtIsNull(String email);

	/**
	 * Come {@link #findByEmailAndDeletedAtIsNull}, ma con un lock pessimistico (SELECT ... FOR UPDATE) fino alla fine
	 * della transazione: due login sullo stesso account si mettono in fila e i contatori non perdono aggiornamenti.
	 * Lo usa anche la reimpostazione della password, che azzera gli stessi contatori.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Utente> findPerLoginByEmailAndDeletedAtIsNull(String email);

	/**
	 * Controlla se l'email è già usata. Non serve filtrare per deletedAt:
	 * l'anonimizzazione sostituisce l'email, che torna così libera.
	 */
	boolean existsByEmail(String email);

	/** Serve al seeder: c'è già un utente con questo ruolo? (il SUPERADMIN non può cancellarsi, vedi me.md) */
	boolean existsByRuolo(Ruolo ruolo);

}
