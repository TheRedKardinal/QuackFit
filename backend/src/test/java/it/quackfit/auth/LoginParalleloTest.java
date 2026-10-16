package it.quackfit.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/**
 * Login sbagliati in parallelo sullo stesso account: con il lock pessimistico si mettono in fila
 * e nessun tentativo si perde. Ripetuto più volte per accorgersi di un'eventuale instabilità.
 */
@SpringBootTest
@ActiveProfiles("test")
class LoginParalleloTest {

	private static final String EMAIL = "parallelo@example.com";
	private static final int TENTATIVI = 2;

	@Autowired
	private AuthService authService;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	@AfterEach
	void pulisci() {
		utenteRepository.deleteAll();
	}

	@RepeatedTest(5)
	void dueErroriContemporaneiContanoEntrambi() throws Exception {
		utenteRepository.save(new Utente("Mario", "Rossi", EMAIL, passwordEncoder.encode("Password1!"),
				Instant.parse("2026-01-01T10:00:00Z")));

		ExecutorService esecutore = Executors.newFixedThreadPool(TENTATIVI);
		CountDownLatch via = new CountDownLatch(1);
		List<Future<?>> risultati = new ArrayList<>();
		for (int i = 0; i < TENTATIVI; i++) {
			risultati.add(esecutore.submit(() -> {
				via.await();
				try {
					authService.login(new LoginRequest(EMAIL, "Sbagliata1!"));
				} catch (ApplicazioneException atteso) {
					// 401: è proprio quello che vogliamo
				}
				return null;
			}));
		}
		via.countDown(); // partono insieme
		for (Future<?> risultato : risultati) {
			risultato.get(30, TimeUnit.SECONDS);
		}
		esecutore.shutdown();

		Utente utente = utenteRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow();
		assertThat(utente.getLoginFalliti()).isEqualTo(TENTATIVI);
	}

}
