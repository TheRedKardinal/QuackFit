package it.quackfit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import it.quackfit.utente.Ruolo;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/**
 * Test di integrazione della sicurezza: applicazione completa con H2 e un controller di prova
 * che esiste solo qui (classe annidata: Spring Boot la esclude dalla scansione degli altri test).
 *
 * Le rotte /api/me e /api/auth/login non hanno ancora un controller: per quelle si verifica solo
 * che la sicurezza lasci passare la richiesta (nessun 401/403), qualunque cosa risponda poi l'endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityConfigTest.ControllerProva.class)
class SecurityConfigTest {

	/**
	 * Data passata, con i millisecondi come nel database: i token generati "adesso" sono sempre successivi.
	 * Le date dei token costruiti a mano sono fisse e nel passato, così non dipendono da quando girano i test.
	 */
	private static final Instant TOKEN_VALIDI_DAL = Instant.parse("2026-01-01T10:00:00.700Z");
	private static final Instant FUTURO_LONTANO = Instant.parse("2100-01-01T00:00:00Z");

	@RestController
	static class ControllerProva {

		@GetMapping("/api/prova")
		String prova(@AuthenticationPrincipal Utente utente) {
			return "Ciao " + utente.getNome();
		}

		@GetMapping("/api/admin/prova")
		String admin() {
			return "admin";
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Value("${quackfit.jwt.segreto}")
	private String segreto;

	@BeforeEach
	void pulisci() {
		utenteRepository.deleteAll();
	}

	// --- utilità ---

	private Utente salva(Ruolo ruolo, boolean emailVerificata) {
		Utente utente = new Utente("Mario", "Rossi", UUID.randomUUID() + "@example.com", "hash", TOKEN_VALIDI_DAL);
		utente.setRuolo(ruolo);
		utente.setEmailVerificata(emailVerificata);
		return utenteRepository.save(utente);
	}

	/** Token costruito a mano, per scegliere emissione e scadenza. */
	private String token(UUID utenteId, Instant emesso, Instant scadenza) {
		return Jwts.builder()
				.subject(utenteId.toString())
				.issuedAt(Date.from(emesso))
				.expiration(Date.from(scadenza))
				.signWith(Keys.hmacShaKeyFor(segreto.getBytes(StandardCharsets.UTF_8)))
				.compact();
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static ResultMatcher errore(int status, String codice) {
		return result -> {
			status().is(status).match(result);
			jsonPath("$.status").value(status).match(result);
			jsonPath("$.codice").value(codice).match(result);
			jsonPath("$.messaggio").isNotEmpty().match(result);
			jsonPath("$.timestamp").isNotEmpty().match(result);
		};
	}

	/** La sicurezza ha lasciato passare la richiesta: niente 401 né 403. */
	private static ResultMatcher nonBloccataDallaSicurezza() {
		return result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}

	// --- token ---

	@Test
	void senzaTokenSuRottaProtetta401() throws Exception {
		mockMvc.perform(get("/api/prova"))
				.andExpect(errore(401, "NON_AUTENTICATO"))
				.andExpect(jsonPath("$.errore").value("Unauthorized"));
	}

	@Test
	void tokenValidoAutenticaConLUtenteComePrincipal() throws Exception {
		Utente utente = salva(Ruolo.UTENTE, true);

		mockMvc.perform(get("/api/prova").header(HttpHeaders.AUTHORIZATION, bearer(jwtService.genera(utente))))
				.andExpect(status().isOk())
				.andExpect(content().string("Ciao Mario"));
	}

	@Test
	void tokenScaduto401() throws Exception {
		Utente utente = salva(Ruolo.UTENTE, true);
		String scaduto = token(utente.getId(), Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T11:00:00Z"));

		mockMvc.perform(get("/api/prova").header(HttpHeaders.AUTHORIZATION, bearer(scaduto)))
				.andExpect(errore(401, "NON_AUTENTICATO"));
	}

	@Test
	void tokenNonValido401() throws Exception {
		mockMvc.perform(get("/api/prova").header(HttpHeaders.AUTHORIZATION, bearer("non-un-jwt")))
				.andExpect(errore(401, "NON_AUTENTICATO"));
	}

	@Test
	void tokenEmessoNelloStessoSecondoDiTokenValidiDalAccettato() throws Exception {
		Utente utente = salva(Ruolo.UTENTE, true);
		// tokenValidiDal = 10:00:00.700, iat = 10:00:00: stesso secondo → valido
		String token = token(utente.getId(), Instant.parse("2026-01-01T10:00:00Z"), FUTURO_LONTANO);

		mockMvc.perform(get("/api/prova").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());
	}

	@Test
	void tokenEmessoUnSecondoPrimaDiTokenValidiDal401() throws Exception {
		Utente utente = salva(Ruolo.UTENTE, true);
		String token = token(utente.getId(), Instant.parse("2026-01-01T09:59:59Z"), FUTURO_LONTANO);

		mockMvc.perform(get("/api/prova").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(errore(401, "NON_AUTENTICATO"));
	}

	@Test
	void utenteCancellato401() throws Exception {
		Utente utente = salva(Ruolo.UTENTE, true);
		String token = jwtService.genera(utente);
		utente.setDeletedAt(TOKEN_VALIDI_DAL);
		utenteRepository.save(utente);

		mockMvc.perform(get("/api/prova").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(errore(401, "NON_AUTENTICATO"));
	}

	@Test
	void utenteInesistente401() throws Exception {
		String token = token(UUID.randomUUID(), Instant.parse("2026-01-01T10:00:00Z"), FUTURO_LONTANO);

		mockMvc.perform(get("/api/prova").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(errore(401, "NON_AUTENTICATO"));
	}

	// --- email verificata ---

	@Test
	void emailNonVerificata403() throws Exception {
		Utente utente = salva(Ruolo.UTENTE, false);

		mockMvc.perform(get("/api/prova").header(HttpHeaders.AUTHORIZATION, bearer(jwtService.genera(utente))))
				.andExpect(errore(403, "EMAIL_NON_VERIFICATA"))
				.andExpect(jsonPath("$.errore").value("Forbidden"));
	}

	@Test
	void leTreRotteDiMeAmmesseSenzaVerifica() throws Exception {
		String token = bearer(jwtService.genera(salva(Ruolo.UTENTE, false)));

		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(nonBloccataDallaSicurezza());
		mockMvc.perform(post("/api/me/verification").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(nonBloccataDallaSicurezza());
		mockMvc.perform(post("/api/me/verification/resend").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(nonBloccataDallaSicurezza());
	}

	@Test
	void altreRotteDiMeRichiedonoLaVerifica() throws Exception {
		String token = bearer(jwtService.genera(salva(Ruolo.UTENTE, false)));

		mockMvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(errore(403, "EMAIL_NON_VERIFICATA"));
	}

	@Test
	void leRotteDiMeSenzaToken401() throws Exception {
		mockMvc.perform(get("/api/me")).andExpect(errore(401, "NON_AUTENTICATO"));
	}

	// --- admin ---

	@Test
	void utenteSuRottaAdmin403AccessoNegato() throws Exception {
		Utente utente = salva(Ruolo.UTENTE, true);

		mockMvc.perform(get("/api/admin/prova").header(HttpHeaders.AUTHORIZATION, bearer(jwtService.genera(utente))))
				.andExpect(errore(403, "ACCESSO_NEGATO"));
	}

	@Test
	void utenteNonVerificatoSuRottaAdmin403AccessoNegato() throws Exception {
		// prima il ruolo, poi l'email: un non admin riceve sempre ACCESSO_NEGATO
		Utente utente = salva(Ruolo.UTENTE, false);

		mockMvc.perform(get("/api/admin/prova").header(HttpHeaders.AUTHORIZATION, bearer(jwtService.genera(utente))))
				.andExpect(errore(403, "ACCESSO_NEGATO"));
	}

	@Test
	void adminNonVerificato403EmailNonVerificata() throws Exception {
		Utente admin = salva(Ruolo.ADMIN, false);

		mockMvc.perform(get("/api/admin/prova").header(HttpHeaders.AUTHORIZATION, bearer(jwtService.genera(admin))))
				.andExpect(errore(403, "EMAIL_NON_VERIFICATA"));
	}

	@Test
	void adminESuperadminVerificatiAmmessi() throws Exception {
		for (Ruolo ruolo : new Ruolo[] { Ruolo.ADMIN, Ruolo.SUPERADMIN }) {
			Utente admin = salva(ruolo, true);

			mockMvc.perform(get("/api/admin/prova").header(HttpHeaders.AUTHORIZATION, bearer(jwtService.genera(admin))))
					.andExpect(status().isOk())
					.andExpect(content().string("admin"));
		}
	}

	@Test
	void rottaAdminSenzaToken401() throws Exception {
		mockMvc.perform(get("/api/admin/prova")).andExpect(errore(401, "NON_AUTENTICATO"));
	}

	// --- rotte pubbliche ---

	@Test
	void tokenScadutoNonImpedisceIlLoginMaSuRottaProtetta401() throws Exception {
		Utente utente = salva(Ruolo.UTENTE, true);
		String scaduto = bearer(
				token(utente.getId(), Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T11:00:00Z")));

		mockMvc.perform(post("/api/auth/login").header(HttpHeaders.AUTHORIZATION, scaduto))
				.andExpect(nonBloccataDallaSicurezza());
		mockMvc.perform(get("/api/prova").header(HttpHeaders.AUTHORIZATION, scaduto))
				.andExpect(errore(401, "NON_AUTENTICATO"));
	}

	@Test
	void tokenNonValidoNonImpedisceIlLogin() throws Exception {
		mockMvc.perform(post("/api/auth/login").header(HttpHeaders.AUTHORIZATION, bearer("non-un-jwt")))
				.andExpect(nonBloccataDallaSicurezza());
	}

	// --- CORS e header ---

	@Test
	void corsAmmetteSoloLOrigineDelFrontend() throws Exception {
		mockMvc.perform(options("/api/prova")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

		mockMvc.perform(options("/api/prova")
						.header(HttpHeaders.ORIGIN, "http://sito-malevolo.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void headerDiSicurezzaPresenti() throws Exception {
		mockMvc.perform(get("/api/prova"))
				.andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "DENY"));
	}

	@Test
	void passwordEncoderUsaBCrypt() {
		String hash = passwordEncoder.encode("Password1!");

		assertThat(hash).startsWith("$2");
		assertThat(passwordEncoder.matches("Password1!", hash)).isTrue();
	}

}
