package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import tools.jackson.databind.ObjectMapper;

/**
 * Test di integrazione di PATCH /api/me e PATCH /api/me/password con H2.
 * PATCH /api/me/password conta nel limite per IP: ogni richiesta arriva da un IP diverso, così il limite non
 * interferisce (il limite stesso è provato in LimiteIpFiltroTest).
 * Il token è costruito a mano ed emesso 10 secondi fa: il filtro confronta tokenValidiDal troncato al secondo, quindi
 * un token emesso nello stesso secondo del cambio password resterebbe valido (vedi auth.md, Note) e il test del
 * logout ovunque non proverebbe nulla.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeModificaControllerTest {

	private static final String PASSWORD = "Password1!";
	private static final AtomicInteger PROSSIMO_IP = new AtomicInteger(1);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private StreakRepository streakRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Value("${quackfit.jwt.segreto}")
	private String segreto;

	private Utente mario;
	private String token;

	@BeforeEach
	void setUp() {
		pulisci();
		mario = new Utente("Mario", "Rossi", "mario@example.com", passwordEncoder.encode(PASSWORD),
				Instant.parse("2026-01-01T10:00:00Z"));
		mario.setEmailVerificata(true);
		mario = utenteRepository.save(mario);
		streakRepository.save(new Streak(mario, Instant.parse("2026-01-01T10:00:00Z")));
		Instant adesso = Instant.now();
		token = "Bearer " + Jwts.builder()
				.subject(mario.getId().toString())
				.issuedAt(Date.from(adesso.minusSeconds(10)))
				.expiration(Date.from(adesso.plusSeconds(3600)))
				.signWith(Keys.hmacShaKeyFor(segreto.getBytes(StandardCharsets.UTF_8)))
				.compact();
	}

	@AfterEach
	void pulisci() {
		streakRepository.deleteAll();
		utenteRepository.deleteAll();
	}

	private static MockHttpServletRequestBuilder daIpNuovo(MockHttpServletRequestBuilder richiesta) {
		String ip = "10.40.0." + PROSSIMO_IP.getAndIncrement();
		return richiesta.with(r -> {
			r.setRemoteAddr(ip);
			return r;
		});
	}

	private ResultActions modificaDati(Object nome, Object cognome) throws Exception {
		Map<String, Object> corpo = new HashMap<>();
		corpo.put("nome", nome);
		corpo.put("cognome", cognome);
		return mockMvc.perform(patch("/api/me")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(corpo)));
	}

	private ResultActions cambiaPassword(String attuale, String nuova) throws Exception {
		Map<String, Object> corpo = new HashMap<>();
		corpo.put("passwordAttuale", attuale);
		corpo.put("nuovaPassword", nuova);
		return mockMvc.perform(daIpNuovo(patch("/api/me/password"))
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(corpo)));
	}

	private Utente marioDalDatabase() {
		return utenteRepository.findById(mario.getId()).orElseThrow();
	}

	private static void validazione(ResultActions risposta, String campo, String messaggio) throws Exception {
		risposta.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi." + campo).value(messaggio));
	}

	// --- PATCH /api/me ---

	@Test
	void modificaDatiValidiPulitiESalvati() throws Exception {
		modificaDati("  Luigi ", " Verdi  ").andExpect(status().isNoContent());

		Utente salvato = marioDalDatabase();
		assertThat(salvato.getNome()).isEqualTo("Luigi");
		assertThat(salvato.getCognome()).isEqualTo("Verdi");
		assertThat(salvato.getEmail()).isEqualTo("mario@example.com");
	}

	@Test
	void modificaDatiNonFaLogout() throws Exception {
		modificaDati("Luigi", "Verdi").andExpect(status().isNoContent());

		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("Luigi"));
	}

	@Test
	void modificaDatiVuotiOMancanti() throws Exception {
		ResultActions risposta = modificaDati("   ", null);

		validazione(risposta, "nome", "Il nome è obbligatorio");
		validazione(risposta, "cognome", "Il cognome è obbligatorio");
		assertThat(marioDalDatabase().getNome()).isEqualTo("Mario");
	}

	@Test
	void modificaDatiOltre50Caratteri() throws Exception {
		validazione(modificaDati("a".repeat(51), "Rossi"), "nome", "Massimo 50 caratteri");
		validazione(modificaDati("Mario", "a".repeat(51)), "cognome", "Massimo 50 caratteri");
	}

	@Test
	void cinquantaCaratteriDopoLaPuliziaVanno() throws Exception {
		modificaDati("  " + "a".repeat(50) + "  ", "Rossi").andExpect(status().isNoContent());
	}

	// --- PATCH /api/me/password ---

	@Test
	void cambioPasswordRiuscitoLogoutOvunqueENuovaPasswordValida() throws Exception {
		cambiaPassword(PASSWORD, "Nuova123!").andExpect(status().isNoContent());

		// il token usato per la chiamata non vale più
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codice").value("NON_AUTENTICATO"));
		// la nuova password funziona al login, la vecchia no
		mockMvc.perform(daIpNuovo(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"mario@example.com\", \"password\": \"Nuova123!\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(daIpNuovo(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"mario@example.com\", \"password\": \"" + PASSWORD + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void passwordAttualeSbagliata400ESessioneIntatta() throws Exception {
		cambiaPassword("Sbagliata1!", "Nuova123!")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("PASSWORD_ATTUALE_ERRATA"));

		assertThat(passwordEncoder.matches(PASSWORD, marioDalDatabase().getPassword())).isTrue();
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
	}

	@Test
	void passwordAttualeOltre72ByteNonDa500() throws Exception {
		cambiaPassword("Aa1!" + "a".repeat(69), "Nuova123!")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("PASSWORD_ATTUALE_ERRATA"));
	}

	@Test
	void nuovaPasswordNonValida() throws Exception {
		validazione(cambiaPassword(PASSWORD, "debole"), "nuovaPassword",
				"La password deve avere almeno 8 caratteri, una maiuscola, un numero e un carattere speciale"
						+ " (massimo 72 byte)");
	}

	@Test
	void nuovaPasswordNonValidaConAttualeSbagliataVinceLaValidazione() throws Exception {
		// prima @Valid, poi il controllo della password attuale (vedi me.md, Cambio password)
		validazione(cambiaPassword("Sbagliata1!", "debole"), "nuovaPassword",
				"La password deve avere almeno 8 caratteri, una maiuscola, un numero e un carattere speciale"
						+ " (massimo 72 byte)");
	}

	@Test
	void campiMancanti() throws Exception {
		ResultActions risposta = cambiaPassword("", null);

		validazione(risposta, "passwordAttuale", "La password attuale è obbligatoria");
		validazione(risposta, "nuovaPassword", "La nuova password è obbligatoria");
	}

	@Test
	void nuovaUgualeAllAttualeErroreSulCampo() throws Exception {
		validazione(cambiaPassword(PASSWORD, PASSWORD), "nuovaPassword",
				"La nuova password deve essere diversa da quella attuale");

		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
	}

	// --- email non verificata ---

	@Test
	void conEmailNonVerificata403SuEntrambi() throws Exception {
		mario.setEmailVerificata(false);
		utenteRepository.save(mario);

		modificaDati("Luigi", "Verdi")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("EMAIL_NON_VERIFICATA"));
		cambiaPassword(PASSWORD, "Nuova123!")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("EMAIL_NON_VERIFICATA"));
	}

}
