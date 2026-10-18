package it.quackfit.utente;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StreakRepository extends JpaRepository<Streak, UUID> {

	Optional<Streak> findByUtente(Utente utente);

	/** Cancellazione dell'account: la streak si elimina definitivamente (vedi me.md). */
	void deleteByUtente(Utente utente);

}
