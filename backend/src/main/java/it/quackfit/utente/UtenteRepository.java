package it.quackfit.utente;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

	/**
	 * Salva solo l'URL della foto, con una UPDATE della sola colonna: dopo l'upload (fuori transazione) non si
	 * riscrive l'intero utente, così non si perdono campi cambiati nel frattempo (es. contatori dei login).
	 */
	@Transactional
	@Modifying
	@Query("update Utente u set u.fotoProfilo = :url where u.id = :id")
	int aggiornaFotoProfilo(@Param("id") UUID id, @Param("url") String url);

}
