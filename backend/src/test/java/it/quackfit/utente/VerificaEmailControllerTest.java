package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import it.quackfit.auth.CodiceService;
import it.quackfit.auth.CodiceVerifica;
import it.quackfit.auth.CodiceVerificaRepository;
import it.quackfit.auth.TipoCodice;
import it.quackfit.mail.MailService;
import it.quackfit.security.JwtService;

/**
 * Test di integrazione di POST /api/me/verification e /verification/resend con H2.
 * L'utente non ha l'email verificata: gli endpoint devono funzionare lo stesso.
 * Niente @Transactional sulla classe: i tentativi si rileggono dal database dopo ogni risposta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificaEmailControllerTest {

	private static final String EMAIL = "mario@example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private CodiceVerificaRepository codiceRepository;

	@Autowired
	private CodiceService codiceService;

	@Autowired
	private JwtService jwtService;

	/** Finto: serve a leggere il codice in chiaro che il servizio "invia". */
	@MockitoBean
	private MailService mailService;

	private Utente utente;
	private String token;

	@BeforeEach
	void setUp() {
		pulisci();
		utente = utenteRepository.save(new Utente("Mario", "Rossi", EMAIL, "hash", Instant.parse("2026-01-01T10:00:00Z")));
		token = "Bearer " + jwtService.genera(utente);
	}

	@AfterEach
	void pulisci() {
		codiceRepository.deleteAll();
		utenteRepository.deleteAll();
	}

	/** Genera un codice come farebbe la registrazione e restituisce quello "inviato" per email. */
	private String generaCodice() {
		clearInvocations(mailService);
		codiceService.generaEInvia(utente, TipoCodice.VERIFICA_EMAIL, null);
		ArgumentCaptor<String> inviato = ArgumentCaptor.forClass(String.class);
		verify(mailService).inviaVerificaEmail(eq(EMAIL), eq("Mario"), inviato.capture());
		return inviato.getValue();
	}

	private static String sbagliato(String giusto) {
		return giusto.equals("000000") ? "111111" : "000000";
	}

	private ResultActions verifica(String codice) throws Exception {
		return mockMvc.perform(post("/api/me/verification")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codice\": \"" + codice + "\"}"));
	}

	private ResultActions reinvia() throws Exception {
		return mockMvc.perform(post("/api/me/verification/resend").header(HttpHeaders.AUTHORIZATION, token));
	}

	private Utente utenteDalDatabase() {
		return utenteRepository.findById(utente.getId()).orElseThrow();
	}

	private CodiceVerifica codiceValidoDalDatabase() {
		return codiceRepository.findFirstByUtenteAndTipoAndUsatoAtIsNullOrderByCreatedAtDesc(utente, TipoCodice.VERIFICA_EMAIL)
				.orElseThrow();
	}

	private static void errore(ResultActions risposta, int stato, String codice) throws Exception {
		risposta.andExpect(status().is(stato)).andExpect(jsonPath("$.codice").value(codice));
	}

	// --- verifica ---

	@Test
	void codiceGiusto204EEmailVerificata() throws Exception {
		String codice = generaCodice();

		verifica(codice).andExpect(status().isNoContent());

		assertThat(utenteDalDatabase().isEmailVerificata()).isTrue();
		assertThat(codiceRepository.findAll()).singleElement()
				.satisfies(usato -> assertThat(usato.getUsatoAt()).isNotNull());
	}

	@Test
	void codiceConSpaziVienePulito() throws Exception {
		String codice = generaCodice();

		verifica("  " + codice + " ").andExpect(status().isNoContent());

		assertThat(utenteDalDatabase().isEmailVerificata()).isTrue();
	}

	@Test
	void codiceSbagliato400EIlTentativoRestaSalvato() throws Exception {
		String codice = generaCodice();

		errore(verifica(sbagliato(codice)), 400, "CODICE_NON_VALIDO");

		assertThat(codiceValidoDalDatabase().getTentativiFalliti()).isEqualTo(1);
		assertThat(utenteDalDatabase().isEmailVerificata()).isFalse();
	}

	@Test
	void codiceScaduto400() throws Exception {
		String codice = generaCodice();
		CodiceVerifica salvato = codiceValidoDalDatabase();
		salvato.setScadenza(Instant.now().minus(Duration.ofMinutes(1)));
		codiceRepository.save(salvato);

		errore(verifica(codice), 400, "CODICE_SCADUTO");

		assertThat(utenteDalDatabase().isEmailVerificata()).isFalse();
	}

	@Test
	void alQuintoErroreTroppiTentativiEPoiNemmenoIlCodiceGiusto() throws Exception {
		String codice = generaCodice();

		for (int i = 0; i < 4; i++) {
			errore(verifica(sbagliato(codice)), 400, "CODICE_NON_VALIDO");
		}
		errore(verifica(sbagliato(codice)), 400, "TROPPI_TENTATIVI");
		errore(verifica(codice), 400, "TROPPI_TENTATIVI");

		assertThat(codiceValidoDalDatabase().getTentativiFalliti()).isEqualTo(5);
		assertThat(utenteDalDatabase().isEmailVerificata()).isFalse();
	}

	@Test
	void codiceVuoto400SenzaContareUnTentativo() throws Exception {
		generaCodice();

		verifica("  ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.codice").value("Il codice è obbligatorio"));

		assertThat(codiceValidoDalDatabase().getTentativiFalliti()).isZero();
	}

	@Test
	void emailGiaVerificata204SenzaModifiche() throws Exception {
		generaCodice();
		utente.setEmailVerificata(true);
		utenteRepository.save(utente);

		verifica("000000").andExpect(status().isNoContent());

		CodiceVerifica codice = codiceValidoDalDatabase();
		assertThat(codice.getTentativiFalliti()).isZero();
		assertThat(codice.getUsatoAt()).isNull();
	}

	// --- reinvio ---

	@Test
	void reinvioEntro60Secondi429() throws Exception {
		generaCodice();
		clearInvocations(mailService);

		errore(reinvia(), 429, "ATTENDI_REINVIO");

		verify(mailService, never()).inviaVerificaEmail(anyString(), anyString(), anyString());
		assertThat(codiceRepository.count()).isEqualTo(1);
	}

	@Test
	void reinvioDopo60SecondiInvalidaIlVecchioEInviaIlNuovo() throws Exception {
		generaCodice();
		CodiceVerifica primo = codiceValidoDalDatabase();
		primo.setCreatedAt(primo.getCreatedAt().minus(Duration.ofMinutes(2)));
		codiceRepository.save(primo);
		clearInvocations(mailService);

		reinvia().andExpect(status().isNoContent());

		ArgumentCaptor<String> nuovo = ArgumentCaptor.forClass(String.class);
		verify(mailService).inviaVerificaEmail(eq(EMAIL), eq("Mario"), nuovo.capture());
		assertThat(codiceRepository.findById(primo.getId()).orElseThrow().getUsatoAt()).isNotNull();
		// il nuovo codice funziona
		verifica(nuovo.getValue()).andExpect(status().isNoContent());
		assertThat(utenteDalDatabase().isEmailVerificata()).isTrue();
	}

	@Test
	void reinvioConEmailGiaVerificata204SenzaInviare() throws Exception {
		utente.setEmailVerificata(true);
		utenteRepository.save(utente);

		reinvia().andExpect(status().isNoContent());

		verify(mailService, never()).inviaVerificaEmail(anyString(), anyString(), anyString());
		assertThat(codiceRepository.count()).isZero();
	}

	// --- accesso ---

	@Test
	void senzaToken401() throws Exception {
		errore(mockMvc.perform(post("/api/me/verification")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codice\": \"123456\"}")), 401, "NON_AUTENTICATO");
		errore(mockMvc.perform(post("/api/me/verification/resend")), 401, "NON_AUTENTICATO");
	}

}
