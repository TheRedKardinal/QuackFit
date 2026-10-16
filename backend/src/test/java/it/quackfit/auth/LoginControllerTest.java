package it.quackfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Test di integrazione di POST /api/auth/login con H2. Niente @Transactional sulla classe: i contatori
 * si rileggono dal database dopo ogni risposta, per verificare che il 401 non li abbia annullati.
 * Ogni richiesta arriva da un IP diverso, così il limite per IP non interferisce.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginControllerTest {

	private static final AtomicInteger PROSSIMO_IP = new AtomicInteger(1);
	private static final String EMAIL = "mario@example.com";
	private static final String PASSWORD = "Password1!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Value("${quackfit.jwt.segreto}")
	private String segreto;

	@BeforeEach
	void creaUtente() {
		utenteRepository.deleteAll();
		utenteRepository.save(new Utente("Mario", "Rossi", EMAIL, passwordEncoder.encode(PASSWORD),
				Instant.parse("2026-01-01T10:00:00Z")));
	}

	@AfterEach
	void pulisci() {
		utenteRepository.deleteAll();
	}

	private Utente dalDatabase() {
		return utenteRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow();
	}

	private ResultActions login(String email, String password, String authorization) throws Exception {
		Map<String, Object> corpo = new HashMap<>();
		corpo.put("email", email);
		corpo.put("password", password);
		String ip = "10.40.0." + PROSSIMO_IP.getAndIncrement();
		var richiesta = post("/api/auth/login")
				.with(r -> {
					r.setRemoteAddr(ip);
					return r;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(corpo));
		if (authorization != null) {
			richiesta.header(HttpHeaders.AUTHORIZATION, authorization);
		}
		return mockMvc.perform(richiesta);
	}

	private ResultActions login(String email, String password) throws Exception {
		return login(email, password, null);
	}

	private static void credenzialiNonValide(ResultActions risposta) throws Exception {
		risposta.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codice").value("CREDENZIALI_NON_VALIDE"))
				.andExpect(jsonPath("$.messaggio")
						.value("Email o password non corretti, oppure troppi tentativi: riprova tra qualche minuto."));
	}

	@Test
	void loginRiuscito200ConIlSoloTokenAncheSenzaVerifica() throws Exception {
		assertThat(dalDatabase().isEmailVerificata()).isFalse();

		login("  MARIO@example.com ", PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void ilContatoreRestaSalvatoAncheSeSiRisponde401() throws Exception {
		credenzialiNonValide(login(EMAIL, "Sbagliata1!"));

		Utente utente = dalDatabase();
		assertThat(utente.getLoginFalliti()).isEqualTo(1);
		assertThat(utente.getPrimoLoginFallitoAt()).isNotNull();
	}

	@Test
	void treErroriRapidiBloccanoAncheLaPasswordGiusta() throws Exception {
		for (int i = 0; i < 3; i++) {
			credenzialiNonValide(login(EMAIL, "Sbagliata1!"));
		}
		assertThat(dalDatabase().getBloccatoFino()).isNotNull();

		credenzialiNonValide(login(EMAIL, PASSWORD));
	}

	@Test
	void loginRiuscitoAzzeraIContatoriNelDatabase() throws Exception {
		credenzialiNonValide(login(EMAIL, "Sbagliata1!"));

		login(EMAIL, PASSWORD).andExpect(status().isOk());

		Utente utente = dalDatabase();
		assertThat(utente.getLoginFalliti()).isZero();
		assertThat(utente.getPrimoLoginFallitoAt()).isNull();
		assertThat(utente.getBloccatoFino()).isNull();
	}

	@Test
	void emailInesistenteEUtenteCancellatoStessoMessaggio() throws Exception {
		credenzialiNonValide(login("nessuno@example.com", PASSWORD));

		Utente cancellato = dalDatabase();
		cancellato.setDeletedAt(Instant.parse("2026-02-01T10:00:00Z"));
		utenteRepository.save(cancellato);

		credenzialiNonValide(login(EMAIL, PASSWORD));
	}

	@Test
	void campiVuoti400SenzaToccareIContatori() throws Exception {
		login("", PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.email").value("L'email è obbligatoria"));
		login(EMAIL, "   ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.password").value("La password è obbligatoria"));
		login(null, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.email").value("L'email è obbligatoria"))
				.andExpect(jsonPath("$.campi.password").value("La password è obbligatoria"));

		assertThat(dalDatabase().getLoginFalliti()).isZero();
	}

	@Test
	void passwordDi73Byte401EContatoreIncrementatoNessun500() throws Exception {
		credenzialiNonValide(login(EMAIL, "Aa1!" + "a".repeat(69)));

		assertThat(dalDatabase().getLoginFalliti()).isEqualTo(1);
	}

	@Test
	void tokenScadutoONonValidoNonImpedisconoIlLogin() throws Exception {
		String scaduto = Jwts.builder()
				.subject(dalDatabase().getId().toString())
				.issuedAt(Date.from(Instant.parse("2026-01-01T10:00:00Z")))
				.expiration(Date.from(Instant.parse("2026-01-01T11:00:00Z")))
				.signWith(Keys.hmacShaKeyFor(segreto.getBytes(StandardCharsets.UTF_8)))
				.compact();

		login(EMAIL, PASSWORD, "Bearer " + scaduto)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
		login(EMAIL, PASSWORD, "Bearer non-un-jwt")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

}
