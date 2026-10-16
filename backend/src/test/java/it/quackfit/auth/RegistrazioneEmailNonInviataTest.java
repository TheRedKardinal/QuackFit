package it.quackfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.net.httpserver.HttpServer;

import it.quackfit.mail.BrevoMailService;
import it.quackfit.mail.MailService;
import it.quackfit.utente.StreakRepository;
import it.quackfit.utente.UtenteRepository;

/**
 * "Un errore d'invio non annulla la registrazione", con l'implementazione vera: BrevoMailService puntato
 * a un HttpServer del JDK che risponde sempre 500 (nessuna libreria di test in più).
 */
@SpringBootTest(properties = {
		"quackfit.mail.modalita=brevo",
		"quackfit.mail.brevo.api-key=chiave-finta-dei-test",
		"quackfit.mail.mittente=noreply@quackfit.it" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrazioneEmailNonInviataTest {

	private static final AtomicInteger CHIAMATE_A_BREVO = new AtomicInteger();
	private static final HttpServer BREVO_GUASTO = avviaBrevoGuasto();

	private static HttpServer avviaBrevoGuasto() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/v3/smtp/email", scambio -> {
				CHIAMATE_A_BREVO.incrementAndGet();
				scambio.getRequestBody().readAllBytes();
				scambio.sendResponseHeaders(500, -1);
				scambio.close();
			});
			server.start();
			return server;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@DynamicPropertySource
	static void urlDiBrevo(DynamicPropertyRegistry registro) {
		registro.add("quackfit.mail.brevo.url",
				() -> "http://127.0.0.1:" + BREVO_GUASTO.getAddress().getPort() + "/v3/smtp/email");
	}

	@AfterAll
	static void fermaBrevo() {
		BREVO_GUASTO.stop(0);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MailService mailService;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private StreakRepository streakRepository;

	@Autowired
	private CodiceVerificaRepository codiceRepository;

	@AfterEach
	void pulisci() {
		codiceRepository.deleteAll();
		streakRepository.deleteAll();
		utenteRepository.deleteAll();
	}

	@Test
	void brevoRisponde500MaLaRegistrazioneRestaValida() throws Exception {
		assertThat(mailService).isInstanceOf(BrevoMailService.class);

		mockMvc.perform(post("/api/auth/register")
						.with(richiesta -> {
							richiesta.setRemoteAddr("10.30.0.1");
							return richiesta;
						})
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nome": "Mario", "cognome": "Rossi", "email": "mario@example.com", "password": "Password1!"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").isNotEmpty());

		assertThat(CHIAMATE_A_BREVO.get()).as("Brevo è stato chiamato davvero").isEqualTo(1);
		assertThat(utenteRepository.findByEmailAndDeletedAtIsNull("mario@example.com")).isPresent();
		assertThat(streakRepository.count()).isEqualTo(1);
		assertThat(codiceRepository.count()).as("il codice resta: l'utente può chiederne uno nuovo").isEqualTo(1);
	}

}
