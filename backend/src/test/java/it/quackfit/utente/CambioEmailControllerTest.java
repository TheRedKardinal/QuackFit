package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import it.quackfit.auth.CodiceVerifica;
import it.quackfit.auth.CodiceVerificaRepository;
import it.quackfit.auth.TipoCodice;
import it.quackfit.mail.MailService;
import tools.jackson.databind.ObjectMapper;

/**
 * Test di integrazione del cambio email con H2 e il CodiceService vero; il MailService è finto e serve a leggere
 * il codice "inviato". POST /api/me/email conta nel limite per IP: ogni richiesta arriva da un IP diverso.
 * Il token è emesso 10 secondi fa, così il logout ovunque della conferma lo rende davvero non valido
 * (iat confrontato al secondo, vedi auth.md, Note).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CambioEmailControllerTest {

	private static final String PASSWORD = "Password1!";
	private static final String NUOVA = "nuova@example.com";
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
	private CodiceVerificaRepository codiceRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Value("${quackfit.jwt.segreto}")
	private String segreto;

	/** Finto: serve a leggere il codice in chiaro che il servizio "invia". */
	@MockitoBean
	private MailService mailService;

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
		codiceRepository.deleteAll();
		streakRepository.deleteAll();
		utenteRepository.deleteAll();
	}

	private static MockHttpServletRequestBuilder daIpNuovo(MockHttpServletRequestBuilder richiesta) {
		String ip = "10.50.0." + PROSSIMO_IP.getAndIncrement();
		return richiesta.with(r -> {
			r.setRemoteAddr(ip);
			return r;
		});
	}

	private ResultActions richiedi(String nuovaEmail, String password) throws Exception {
		Map<String, Object> corpo = new HashMap<>();
		corpo.put("nuovaEmail", nuovaEmail);
		corpo.put("password", password);
		return mockMvc.perform(daIpNuovo(post("/api/me/email"))
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(corpo)));
	}

	private ResultActions conferma(String codice) throws Exception {
		return mockMvc.perform(post("/api/me/email/confirm")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codice\": \"" + codice + "\"}"));
	}

	/** Chiede il cambio e restituisce il codice "inviato" alla nuova email. */
	private String codiceInviato() throws Exception {
		clearInvocations(mailService);
		richiedi(NUOVA, PASSWORD).andExpect(status().isNoContent());
		ArgumentCaptor<String> codice = ArgumentCaptor.forClass(String.class);
		verify(mailService).inviaConfermaCambioEmail(eq(NUOVA), eq("Mario"), codice.capture());
		return codice.getValue();
	}

	private Utente marioDalDatabase() {
		return utenteRepository.findById(mario.getId()).orElseThrow();
	}

	private static void errore(ResultActions risposta, int stato, String codice) throws Exception {
		risposta.andExpect(status().is(stato)).andExpect(jsonPath("$.codice").value(codice));
	}

	// --- richiesta ---

	@Test
	void richiestaValidaCodiceAllaNuovaEmailEdEmailNonAncoraCambiata() throws Exception {
		codiceInviato();

		assertThat(marioDalDatabase().getEmail()).isEqualTo("mario@example.com");
		CodiceVerifica codice = codiceRepository
				.findFirstByUtenteAndTipoAndUsatoAtIsNullOrderByCreatedAtDesc(mario, TipoCodice.CAMBIO_EMAIL).orElseThrow();
		assertThat(codice.getNuovaEmail()).isEqualTo(NUOVA);
	}

	@Test
	void laNuovaEmailVienePulita() throws Exception {
		richiedi("  Nuova@Example.COM ", PASSWORD).andExpect(status().isNoContent());

		verify(mailService).inviaConfermaCambioEmail(eq(NUOVA), eq("Mario"), anyString());
	}

	@Test
	void passwordSbagliata() throws Exception {
		errore(richiedi(NUOVA, "Sbagliata1!"), 400, "PASSWORD_ATTUALE_ERRATA");
		verify(mailService, never()).inviaConfermaCambioEmail(anyString(), anyString(), anyString());
	}

	@Test
	void emailNonValidaOMancante() throws Exception {
		richiedi("mario@localhost", PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.nuovaEmail").value("Formato email non valido"));
		richiedi("", "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.nuovaEmail").value("L'email è obbligatoria"))
				.andExpect(jsonPath("$.campi.password").value("La password è obbligatoria"));
	}

	@Test
	void stessaEmailAncheConMaiuscoleESpazi() throws Exception {
		richiedi(" MARIO@example.com ", PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.nuovaEmail").value("La nuova email deve essere diversa da quella attuale"));
	}

	@Test
	void emailGiaUsataAllaRichiesta() throws Exception {
		utenteRepository.save(new Utente("Luigi", "Verdi", NUOVA, "hash", Instant.parse("2026-01-01T10:00:00Z")));

		errore(richiedi(NUOVA, PASSWORD), 409, "EMAIL_GIA_USATA");
		verify(mailService, never()).inviaConfermaCambioEmail(anyString(), anyString(), anyString());
	}

	@Test
	void nuovaRichiestaEntro60Secondi429() throws Exception {
		codiceInviato();

		errore(richiedi("altra@example.com", PASSWORD), 429, "ATTENDI_REINVIO");
	}

	// --- conferma ---

	@Test
	void confermaRiuscitaEmailCambiataVerificataELogoutOvunque() throws Exception {
		conferma(codiceInviato()).andExpect(status().isNoContent());

		Utente salvato = marioDalDatabase();
		assertThat(salvato.getEmail()).isEqualTo(NUOVA);
		assertThat(salvato.isEmailVerificata()).isTrue();
		// il token usato non vale più; il login funziona con la nuova email, non con la vecchia
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(daIpNuovo(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"" + NUOVA + "\", \"password\": \"" + PASSWORD + "\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(daIpNuovo(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"mario@example.com\", \"password\": \"" + PASSWORD + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void codiceSbagliatoEmailInvariata() throws Exception {
		String giusto = codiceInviato();

		errore(conferma(giusto.equals("000000") ? "111111" : "000000"), 400, "CODICE_NON_VALIDO");
		assertThat(marioDalDatabase().getEmail()).isEqualTo("mario@example.com");
	}

	@Test
	void codiceScaduto() throws Exception {
		String giusto = codiceInviato();
		CodiceVerifica codice = codiceRepository
				.findFirstByUtenteAndTipoAndUsatoAtIsNullOrderByCreatedAtDesc(mario, TipoCodice.CAMBIO_EMAIL).orElseThrow();
		codice.setScadenza(Instant.now().minus(Duration.ofMinutes(1)));
		codiceRepository.save(codice);

		errore(conferma(giusto), 400, "CODICE_SCADUTO");
		assertThat(marioDalDatabase().getEmail()).isEqualTo("mario@example.com");
	}

	@Test
	void senzaRichiestaNessunCodice() throws Exception {
		errore(conferma("123456"), 400, "CODICE_NON_VALIDO");
	}

	@Test
	void emailPresaNelFrattempoAllaConferma409() throws Exception {
		String codice = codiceInviato();
		utenteRepository.save(new Utente("Luigi", "Verdi", NUOVA, "hash", Instant.parse("2026-01-01T10:00:00Z")));

		errore(conferma(codice), 409, "EMAIL_GIA_USATA");
		assertThat(marioDalDatabase().getEmail()).isEqualTo("mario@example.com");
		// la sessione resta valida
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
	}

	// --- email non verificata ---

	@Test
	void conEmailNonVerificata403() throws Exception {
		mario.setEmailVerificata(false);
		utenteRepository.save(mario);

		errore(richiedi(NUOVA, PASSWORD), 403, "EMAIL_NON_VERIFICATA");
		errore(conferma("123456"), 403, "EMAIL_NON_VERIFICATA");
	}

}
