package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class StreakRepositoryTest {

	private static final Instant ADESSO = Instant.parse("2026-10-16T10:00:00Z");

	@Autowired
	private StreakRepository streakRepository;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private TestEntityManager entityManager;

	private Utente salvaUtente() {
		return utenteRepository.saveAndFlush(new Utente("Mario", "Rossi", "mario@example.com", "hash-bcrypt", ADESSO));
	}

	@Test
	void salvaStreakIniziale() {
		Utente utente = salvaUtente();
		streakRepository.saveAndFlush(new Streak(utente, ADESSO));
		entityManager.clear();

		Streak letta = streakRepository.findByUtente(utente).orElseThrow();
		assertThat(letta.getId()).isNotNull();
		assertThat(letta.getValore()).isZero();
		assertThat(letta.getRecord()).isZero();
		assertThat(letta.getUltimaAttivita()).isNull();
		assertThat(letta.getUpdatedAt()).isEqualTo(ADESSO);
		assertThat(letta.getUtente().getId()).isEqualTo(utente.getId());
	}

	@Test
	void unaSolaStreakPerUtente() {
		Utente utente = salvaUtente();
		streakRepository.saveAndFlush(new Streak(utente, ADESSO));

		assertThatThrownBy(() -> streakRepository.saveAndFlush(new Streak(utente, ADESSO)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
