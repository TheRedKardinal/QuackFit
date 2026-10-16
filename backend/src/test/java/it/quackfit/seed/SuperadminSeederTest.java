package it.quackfit.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import it.quackfit.utente.Ruolo;
import it.quackfit.utente.StreakRepository;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/**
 * Test del seeder del superadmin con H2. Nel profilo test il seeder non parte da solo:
 * lo si costruisce e lo si esegue a mano, simulando uno o più avvii dell'app.
 */
@SpringBootTest
@ActiveProfiles("test")
class SuperadminSeederTest {

	private static final String EMAIL = "admin@quackfit.it";
	private static final String PASSWORD = "Superadmin1!";

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private StreakRepository streakRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private Clock clock;

	@BeforeEach
	@AfterEach
	void pulisci() {
		streakRepository.deleteAll();
		utenteRepository.deleteAll();
	}

	/** Un "avvio" dell'app con queste chiavi in env.properties. */
	private void avvio(String email, String password) {
		new SuperadminSeeder(utenteRepository, streakRepository, passwordEncoder, clock, email, password).run();
	}

	private long superadmin() {
		return utenteRepository.findAll().stream().filter(u -> u.getRuolo() == Ruolo.SUPERADMIN).count();
	}

	@Test
	void primoAvvioCreaIlSuperadminConEmailVerificataEStreak() {
		avvio("  Admin@QuackFit.it ", PASSWORD);

		Utente creato = utenteRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow();
		assertThat(creato.getRuolo()).isEqualTo(Ruolo.SUPERADMIN);
		assertThat(creato.getNome()).isEqualTo("Super");
		assertThat(creato.getCognome()).isEqualTo("Admin");
		assertThat(creato.isEmailVerificata()).isTrue();
		assertThat(passwordEncoder.matches(PASSWORD, creato.getPassword())).isTrue();
		assertThat(streakRepository.findAll()).singleElement()
				.satisfies(streak -> assertThat(streak.getUtente().getId()).isEqualTo(creato.getId()));
	}

	@Test
	void secondoAvvioNonDuplica() {
		avvio(EMAIL, PASSWORD);
		avvio(EMAIL, PASSWORD);

		assertThat(superadmin()).isEqualTo(1);
		assertThat(utenteRepository.count()).isEqualTo(1);
		assertThat(streakRepository.count()).isEqualTo(1);
	}

	@Test
	void conIlSuperadminGiaPresenteLeChiaviNonServono() {
		avvio(EMAIL, PASSWORD);

		// chiavi tolte o cambiate dopo il primo avvio: nessun errore e nessun nuovo superadmin
		avvio("", "");
		avvio("altro@quackfit.it", "Altra123!");

		assertThat(superadmin()).isEqualTo(1);
		assertThat(utenteRepository.findByEmailAndDeletedAtIsNull(EMAIL)).isPresent();
	}

	@ParameterizedTest
	@CsvSource(value = { "'', Superadmin1!", "admin@quackfit.it, ''", "'  ', '   '", "NULL, NULL" }, nullValues = "NULL")
	void chiaviMancantiOVuoteL_AppNonParte(String email, String password) {
		assertThatThrownBy(() -> avvio(email, password))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SUPERADMIN_EMAIL e SUPERADMIN_PASSWORD");
		assertThat(utenteRepository.count()).isZero();
	}

	@Test
	void emailNonValidaL_AppNonParte() {
		assertThatThrownBy(() -> avvio("admin@localhost", PASSWORD))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SUPERADMIN_EMAIL");
		assertThat(utenteRepository.count()).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "corta1!", "senzamaiuscole1!", "SenzaNumeri!", "SenzaSpeciali1",
			"Aa1!aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" })
	void passwordNonValidaL_AppNonParte(String password) {
		assertThatThrownBy(() -> avvio(EMAIL, password))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SUPERADMIN_PASSWORD");
		assertThat(utenteRepository.count()).isZero();
	}

	@Test
	void emailGiaUsataDaUnUtenteL_AppNonParteESenzaPromuoverlo() {
		utenteRepository.save(new Utente("Mario", "Rossi", EMAIL, "hash", Instant.now(clock)));

		assertThatThrownBy(() -> avvio(EMAIL, PASSWORD))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("già usata");
		assertThat(superadmin()).isZero();
		assertThat(utenteRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow().getRuolo())
				.isEqualTo(Ruolo.UTENTE);
	}

}
