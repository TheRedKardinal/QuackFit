package it.quackfit.utente;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface StreakRepository extends JpaRepository<Streak, UUID> {

	Optional<Streak> findByUtente(Utente utente);

	/**
	 * La streak dell'utente con un lock pessimistico fino alla fine della transazione: due lezioni chiuse quasi insieme
	 * non perdono un aggiornamento (vedi backend/CLAUDE.md, Regole ricorrenti, Contatori).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Streak s where s.utente.id = :utenteId")
	Optional<Streak> bloccaDellUtente(@Param("utenteId") UUID utenteId);

	/** Cancellazione dell'account: la streak si elimina definitivamente (vedi me.md). */
	void deleteByUtente(Utente utente);

}
