package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class UtenteRepositoryTest {

	private static final Instant ADESSO = Instant.parse("2026-10-16T10:00:00Z");

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private TestEntityManager entityManager;

	private Utente nuovoUtente(String email) {
		return new Utente("Mario", "Rossi", email, "hash-bcrypt", ADESSO);
	}

	@Test
	void salvaUtenteConIValoriIniziali() {
		Utente salvato = utenteRepository.saveAndFlush(nuovoUtente("mario@example.com"));
		entityManager.clear();

		Utente letto = utenteRepository.findById(salvato.getId()).orElseThrow();
		assertThat(letto.getId()).isNotNull();
		assertThat(letto.getRuolo()).isEqualTo(Ruolo.UTENTE);
		assertThat(letto.isEmailVerificata()).isFalse();
		assertThat(letto.getLoginFalliti()).isZero();
		assertThat(letto.getTokenValidiDal()).isEqualTo(ADESSO);
		assertThat(letto.getCreatedAt()).isEqualTo(ADESSO);
		assertThat(letto.getFotoProfilo()).isNull();
		assertThat(letto.getPrimoLoginFallitoAt()).isNull();
		assertThat(letto.getBloccatoFino()).isNull();
		assertThat(letto.getDeletedAt()).isNull();
	}

	@Test
	void trovaUtenteAttivoPerEmail() {
		utenteRepository.saveAndFlush(nuovoUtente("mario@example.com"));

		Optional<Utente> trovato = utenteRepository.findByEmailAndDeletedAtIsNull("mario@example.com");

		assertThat(trovato).isPresent();
		assertThat(trovato.get().getNome()).isEqualTo("Mario");
	}

	@Test
	void nonTrovaEmailInesistente() {
		utenteRepository.saveAndFlush(nuovoUtente("mario@example.com"));

		assertThat(utenteRepository.findByEmailAndDeletedAtIsNull("luigi@example.com")).isEmpty();
	}

	@Test
	void nonTrovaUtenteCancellato() {
		Utente cancellato = nuovoUtente("mario@example.com");
		cancellato.setDeletedAt(ADESSO);
		utenteRepository.saveAndFlush(cancellato);

		assertThat(utenteRepository.findByEmailAndDeletedAtIsNull("mario@example.com")).isEmpty();
	}

	@Test
	void existsByEmailRiconosceLeEmailGiaUsate() {
		utenteRepository.saveAndFlush(nuovoUtente("mario@example.com"));

		assertThat(utenteRepository.existsByEmail("mario@example.com")).isTrue();
		assertThat(utenteRepository.existsByEmail("luigi@example.com")).isFalse();
	}

	@Test
	void emailDuplicataRifiutataDalDatabase() {
		utenteRepository.saveAndFlush(nuovoUtente("mario@example.com"));

		assertThatThrownBy(() -> utenteRepository.saveAndFlush(nuovoUtente("mario@example.com")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
