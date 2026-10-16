package it.quackfit.utente;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StreakRepository extends JpaRepository<Streak, UUID> {

	Optional<Streak> findByUtente(Utente utente);

}
