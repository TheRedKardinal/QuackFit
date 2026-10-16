package it.quackfit.seed;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.common.PasswordValidaValidator;
import it.quackfit.utente.Ruolo;
import it.quackfit.utente.Streak;
import it.quackfit.utente.StreakRepository;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * All'avvio garantisce che esista un SUPERADMIN (vedi backend/CLAUDE.md, Seeder, e admin.md).
 * Se esiste già non fa nulla, anche se nel frattempo SUPERADMIN_EMAIL è cambiata. Altrimenti lo crea con email e
 * password da env.properties, email già verificata e la sua Streak; se i dati non vanno bene l'app non parte.
 *
 * Non gira nel profilo test: il superadmin con la sua streak resterebbe nel database condiviso dai test.
 * Il suo test lo chiama a mano.
 */
@Slf4j
@Component
@Profile("!test")
public class SuperadminSeeder implements CommandLineRunner {

	static final String NOME = "Super";
	static final String COGNOME = "Admin";

	private final UtenteRepository utenteRepository;
	private final StreakRepository streakRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final String email;
	private final String password;

	public SuperadminSeeder(UtenteRepository utenteRepository, StreakRepository streakRepository,
			PasswordEncoder passwordEncoder, Clock clock,
			@Value("${quackfit.superadmin.email}") String email,
			@Value("${quackfit.superadmin.password}") String password) {
		this.utenteRepository = utenteRepository;
		this.streakRepository = streakRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		// stessa pulizia della registrazione; la password non si tocca
		this.email = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
		this.password = password == null ? "" : password;
	}

	/**
	 * @throws IllegalStateException se il superadmin manca e le chiavi non sono valide: l'avvio si interrompe
	 */
	@Override
	@Transactional
	public void run(String... argomenti) {
		if (utenteRepository.existsByRuolo(Ruolo.SUPERADMIN)) {
			return;
		}
		if (email.isEmpty() || password.isBlank()) {
			throw new IllegalStateException(
					"Nessun SUPERADMIN nel database: imposta SUPERADMIN_EMAIL e SUPERADMIN_PASSWORD");
		}
		// stesso controllo minimo della registrazione: un punto nel dominio
		if (!email.matches(".+@.+\\..+")) {
			throw new IllegalStateException("SUPERADMIN_EMAIL non è un'email valida");
		}
		// stesse regole di @PasswordValida (il contesto non serve al validatore)
		if (!new PasswordValidaValidator().isValid(password, null)) {
			throw new IllegalStateException("SUPERADMIN_PASSWORD deve avere almeno 8 caratteri, una maiuscola, "
					+ "un numero e un carattere speciale (massimo 72 byte)");
		}
		// mai promuovere di nascosto un utente esistente
		if (utenteRepository.existsByEmail(email)) {
			throw new IllegalStateException("SUPERADMIN_EMAIL è già usata da un altro utente: scegline un'altra");
		}

		Instant adesso = Instant.now(clock);
		Utente superadmin = new Utente(NOME, COGNOME, email, passwordEncoder.encode(password), adesso);
		superadmin.setRuolo(Ruolo.SUPERADMIN);
		superadmin.setEmailVerificata(true);
		superadmin = utenteRepository.save(superadmin);
		streakRepository.save(new Streak(superadmin, adesso));
		log.info("SUPERADMIN creato");
	}

}
