package it.quackfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import it.quackfit.utente.StreakRepository;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Test di integrazione di POST /api/auth/register: applicazione completa con H2 ed email nel log.
 * Ogni richiesta arriva da un IP diverso, così il limite di 20 richieste al minuto per IP non interferisce.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RegistrazioneControllerTest.ControllerProva.class)
class RegistrazioneControllerTest {

	private static final AtomicInteger PROSSIMO_IP = new AtomicInteger(1);

	private static final String MSG_PASSWORD =
			"La password deve avere almeno 8 caratteri, una maiuscola, un numero e un carattere speciale (massimo 72 byte)";

	/** Rotta protetta qualunque (non tra quelle ammesse senza verifica), solo per questo test. */
	@RestController
	static class ControllerProva {

		@GetMapping("/api/prova-registrazione")
		String prova() {
			return "ok";
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private StreakRepository streakRepository;

	@Autowired
	private CodiceVerificaRepository codiceRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	@AfterEach
	void pulisci() {
		// prima chi ha la chiave esterna verso l'utente
		codiceRepository.deleteAll();
		streakRepository.deleteAll();
		utenteRepository.deleteAll();
	}

	private static Map<String, Object> corpo(String nome, String cognome, String email, String password) {
		Map<String, Object> corpo = new HashMap<>();
		corpo.put("nome", nome);
		corpo.put("cognome", cognome);
		corpo.put("email", email);
		corpo.put("password", password);
		return corpo;
	}

	private static Map<String, Object> valido() {
		return corpo("Mario", "Rossi", "mario@example.com", "Password1!");
	}

	private ResultActions registra(Map<String, Object> corpo) throws Exception {
		String ip = "10.20.0." + PROSSIMO_IP.getAndIncrement();
		return mockMvc.perform(post("/api/auth/register")
				.with(richiesta -> {
					richiesta.setRemoteAddr(ip);
					return richiesta;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(corpo)));
	}

	@Test
	void registrazioneValidaRisponde201ConIlSoloToken() throws Exception {
		registra(valido())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.length()").value(1));

		Utente utente = utenteRepository.findByEmailAndDeletedAtIsNull("mario@example.com").orElseThrow();
		assertThat(utente.isEmailVerificata()).isFalse();
		assertThat(passwordEncoder.matches("Password1!", utente.getPassword())).isTrue();
		assertThat(streakRepository.count()).isEqualTo(1);
		assertThat(codiceRepository.findAll()).singleElement()
				.satisfies(codice -> assertThat(codice.getTipo()).isEqualTo(TipoCodice.VERIFICA_EMAIL));
	}

	@Test
	void ilTokenFunzionaSubitoMaServeLaVerifica() throws Exception {
		String risposta = registra(valido()).andReturn().getResponse().getContentAsString();
		String token = objectMapper.readTree(risposta).get("token").asString();

		// 403 e non 401: il token è valido (emesso nello stesso secondo di tokenValidiDal), manca solo la verifica
		mockMvc.perform(get("/api/prova-registrazione").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("EMAIL_NON_VERIFICATA"));
	}

	@Test
	void lInputVienePulito() throws Exception {
		registra(corpo("  Mario ", " Rossi  ", "  Mario@Example.COM ", "Password1!"))
				.andExpect(status().isCreated());

		Utente utente = utenteRepository.findByEmailAndDeletedAtIsNull("mario@example.com").orElseThrow();
		assertThat(utente.getNome()).isEqualTo("Mario");
		assertThat(utente.getCognome()).isEqualTo("Rossi");
	}

	@Test
	void emailGiaUsata409() throws Exception {
		registra(valido()).andExpect(status().isCreated());

		// stessa email scritta diversamente: dopo la pulizia è la stessa
		registra(corpo("Luigi", "Verdi", " MARIO@example.com", "Password1!"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codice").value("EMAIL_GIA_USATA"))
				.andExpect(jsonPath("$.messaggio")
						.value("Email già registrata. Se è tua, accedi oppure usa 'Password dimenticata'."));

		assertThat(utenteRepository.count()).isEqualTo(1);
	}

	static Stream<Arguments> campiNonValidi() {
		String emailDa255 = "a".repeat(64) + "@" + "b".repeat(60) + "." + "c".repeat(60) + "." + "d".repeat(60) + "."
				+ "e".repeat(7);
		return Stream.of(
				Arguments.of("nome", "", "Il nome è obbligatorio"),
				Arguments.of("nome", "   ", "Il nome è obbligatorio"),
				Arguments.of("nome", null, "Il nome è obbligatorio"),
				Arguments.of("nome", "a".repeat(51), "Massimo 50 caratteri"),
				Arguments.of("cognome", "", "Il cognome è obbligatorio"),
				Arguments.of("cognome", "a".repeat(51), "Massimo 50 caratteri"),
				Arguments.of("email", "", "L'email è obbligatoria"),
				Arguments.of("email", "   ", "L'email è obbligatoria"),
				Arguments.of("email", "non-una-email", "Formato email non valido"),
				Arguments.of("email", "mario@localhost", "Formato email non valido"),
				Arguments.of("email", emailDa255, "Massimo 254 caratteri"),
				Arguments.of("password", "", "La password è obbligatoria"),
				Arguments.of("password", "   ", "La password è obbligatoria"),
				Arguments.of("password", "Pass1!", MSG_PASSWORD),
				Arguments.of("password", "password1!", MSG_PASSWORD),
				Arguments.of("password", "Password!!", MSG_PASSWORD),
				Arguments.of("password", "Password12", MSG_PASSWORD),
				Arguments.of("password", "Aa1!" + "a".repeat(69), MSG_PASSWORD));
	}

	@ParameterizedTest(name = "{0} = \"{1}\"")
	@MethodSource("campiNonValidi")
	void campoNonValido400(String campo, String valore, String messaggio) throws Exception {
		Map<String, Object> corpo = valido();
		corpo.put(campo, valore);

		registra(corpo)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.length()").value(1))
				.andExpect(jsonPath("$.campi." + campo).value(messaggio));

		assertThat(utenteRepository.count()).isZero();
	}

}
