package it.quackfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import it.quackfit.common.ClockConfig;
import it.quackfit.mail.MailService;
import it.quackfit.security.JwtService;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/**
 * Test di integrazione di POST /api/auth/password/forgot e /password/reset con H2.
 * Nel profilo test l'executor di @Async è sincrono: quando forgot risponde, il codice è già stato creato e "inviato".
 * Niente @Transactional sulla classe: utente e codici si rileggono dal database dopo ogni risposta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecuperoPasswordControllerTest {

	private static final String EMAIL = "mario@example.com";
	private static final String PASSWORD = "Password1!";
	private static final String NUOVA_PASSWORD = "Nuova123!";

	/** Un IP diverso per ogni richiesta: il limite di 20 richieste al minuto per IP non deve scattare. */
	private static final AtomicInteger PROSSIMO_IP = new AtomicInteger(1);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private CodiceVerificaRepository codiceRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private Clock clock;

	@Value("${quackfit.jwt.segreto}")
	private String segretoJwt;

	@Value("${quackfit.jwt.durata}")
	private Duration durataJwt;

	/** Finto: serve a leggere il codice in chiaro che il servizio "invia". */
	@MockitoBean
	private MailService mailService;

	private Utente utente;

	@BeforeEach
	void setUp() {
		pulisci();
		utente = utenteRepository.save(new Utente("Mario", "Rossi", EMAIL, passwordEncoder.encode(PASSWORD),
				Instant.now(clock).minus(Duration.ofDays(1))));
	}

	@AfterEach
	void pulisci() {
		codiceRepository.deleteAll();
		utenteRepository.deleteAll();
	}

	/** POST su /api/auth/** da un IP sempre nuovo. */
	private static MockHttpServletRequestBuilder postAuth(String url) {
		String ip = "10.60.0." + PROSSIMO_IP.getAndIncrement();
		return post(url).with(richiesta -> {
			richiesta.setRemoteAddr(ip);
			return richiesta;
		});
	}

	private ResultActions forgot(String email) throws Exception {
		return mockMvc.perform(postAuth("/api/auth/password/forgot")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"" + email + "\"}"));
	}

	private ResultActions reset(String email, String codice, String nuovaPassword) throws Exception {
		return mockMvc.perform(postAuth("/api/auth/password/reset")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"" + email + "\", \"codice\": \"" + codice + "\", \"nuovaPassword\": \""
						+ nuovaPassword + "\"}"));
	}

	/** Chiede il codice con forgot e restituisce quello "inviato" per email. */
	private String codiceInviato() throws Exception {
		clearInvocations(mailService);
		forgot(EMAIL).andExpect(status().isOk());
		ArgumentCaptor<String> inviato = ArgumentCaptor.forClass(String.class);
		verify(mailService).inviaResetPassword(eq(EMAIL), eq("Mario"), inviato.capture());
		return inviato.getValue();
	}

	private static String sbagliato(String giusto) {
		return giusto.equals("000000") ? "111111" : "000000";
	}

	private Utente utenteDalDatabase() {
		return utenteRepository.findById(utente.getId()).orElseThrow();
	}

	private List<CodiceVerifica> codiciDalDatabase() {
		return codiceRepository.findAll();
	}

	/** Status e corpo della risposta, per confrontare risposte che devono essere identiche. */
	private record Risposta(int status, String corpo) {
		static Risposta di(MvcResult risultato) throws Exception {
			return new Risposta(risultato.getResponse().getStatus(), risultato.getResponse().getContentAsString());
		}
	}

	// --- password dimenticata ---

	@Test
	void emailRegistrata200ECodiceCreatoEInviato() throws Exception {
		forgot(EMAIL)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messaggio").value(AuthController.MSG_PASSWORD_DIMENTICATA));

		assertThat(codiciDalDatabase()).singleElement()
				.satisfies(codice -> assertThat(codice.getTipo()).isEqualTo(TipoCodice.RESET_PASSWORD));
		verify(mailService).inviaResetPassword(eq(EMAIL), eq("Mario"), anyString());
	}

	@Test
	void stessaRispostaPerEmailRegistrataInesistenteEInAttesaDelReinvio() throws Exception {
		Risposta registrata = Risposta.di(forgot(EMAIL).andReturn());
		Risposta inAttesa = Risposta.di(forgot(EMAIL).andReturn()); // meno di 60 secondi dopo
		Risposta inesistente = Risposta.di(forgot("nessuno@example.com").andReturn());

		assertThat(registrata.status()).isEqualTo(200);
		assertThat(inAttesa).isEqualTo(registrata);
		assertThat(inesistente).isEqualTo(registrata);

		// un solo codice: il secondo invio è stato saltato in silenzio, senza 429
		assertThat(codiciDalDatabase()).hasSize(1);
		verify(mailService).inviaResetPassword(eq(EMAIL), eq("Mario"), anyString());
	}

	@Test
	void emailInesistenteNessunCodice() throws Exception {
		forgot("nessuno@example.com").andExpect(status().isOk());

		assertThat(codiciDalDatabase()).isEmpty();
		verifyNoInteractions(mailService);
	}

	@Test
	void utenteCancellatoNessunCodice() throws Exception {
		utente.setDeletedAt(Instant.now(clock));
		utenteRepository.save(utente);

		forgot(EMAIL).andExpect(status().isOk());

		assertThat(codiciDalDatabase()).isEmpty();
		verifyNoInteractions(mailService);
	}

	@Test
	void dopo60SecondiUnNuovoCodice() throws Exception {
		forgot(EMAIL).andExpect(status().isOk());
		CodiceVerifica primo = codiciDalDatabase().get(0);
		primo.setCreatedAt(primo.getCreatedAt().minus(Duration.ofSeconds(61)));
		codiceRepository.save(primo);

		forgot(EMAIL).andExpect(status().isOk());

		assertThat(codiciDalDatabase()).hasSize(2);
	}

	@Test
	void emailConSpaziEMaiuscoleVienePulita() throws Exception {
		forgot("  MARIO@Example.com ").andExpect(status().isOk());

		assertThat(codiciDalDatabase()).hasSize(1);
	}

	@Test
	void emailVuota400Validazione() throws Exception {
		forgot(" ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.email").value("L'email è obbligatoria"));
	}

	// --- reimpostazione ---

	@Test
	void codiceGiusto204EPasswordCambiataEmailVerificataLoginSbloccato() throws Exception {
		utente.setLoginFalliti(3);
		utente.setPrimoLoginFallitoAt(Instant.now(clock).minus(Duration.ofMinutes(1)));
		utente.setBloccatoFino(Instant.now(clock).plus(Duration.ofMinutes(10)));
		utenteRepository.save(utente);
		String codice = codiceInviato();
		Instant prima = Instant.now(clock);

		reset(EMAIL, codice, NUOVA_PASSWORD).andExpect(status().isNoContent());

		Utente aggiornato = utenteDalDatabase();
		assertThat(passwordEncoder.matches(NUOVA_PASSWORD, aggiornato.getPassword())).isTrue();
		assertThat(aggiornato.isEmailVerificata()).isTrue();
		assertThat(aggiornato.getTokenValidiDal()).isAfterOrEqualTo(prima);
		assertThat(aggiornato.getLoginFalliti()).isZero();
		assertThat(aggiornato.getPrimoLoginFallitoAt()).isNull();
		assertThat(aggiornato.getBloccatoFino()).isNull();
		assertThat(codiciDalDatabase()).singleElement()
				.satisfies(usato -> assertThat(usato.getUsatoAt()).isNotNull());

		// il login con la nuova password funziona subito, anche se prima l'account era bloccato
		mockMvc.perform(postAuth("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"" + EMAIL + "\", \"password\": \"" + NUOVA_PASSWORD + "\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void iTokenPrecedentiVengonoRifiutati() throws Exception {
		// token emesso un minuto fa (dopo token_validi_dal, quindi valido fino al reset)
		JwtService jwtNelPassato = new JwtService(segretoJwt, durataJwt,
				Clock.fixed(Instant.now(clock).minus(Duration.ofMinutes(1)), ClockConfig.FUSO));
		String vecchioToken = "Bearer " + jwtNelPassato.genera(utente);
		mockMvc.perform(post("/api/me/verification/resend").header(HttpHeaders.AUTHORIZATION, vecchioToken))
				.andExpect(status().isNoContent());
		String codice = codiceInviato();

		reset(EMAIL, codice, NUOVA_PASSWORD).andExpect(status().isNoContent());

		mockMvc.perform(post("/api/me/verification/resend").header(HttpHeaders.AUTHORIZATION, vecchioToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codice").value("NON_AUTENTICATO"));
	}

	@Test
	void codiceSbagliato400EIlTentativoRestaSalvato() throws Exception {
		String codice = codiceInviato();

		reset(EMAIL, sbagliato(codice), NUOVA_PASSWORD).andExpect(status().isBadRequest());

		assertThat(codiciDalDatabase()).singleElement()
				.satisfies(salvato -> assertThat(salvato.getTentativiFalliti()).isEqualTo(1));
		assertThat(passwordEncoder.matches(PASSWORD, utenteDalDatabase().getPassword())).isTrue();
	}

	@Test
	void dopoCinqueErroriAncheIlCodiceGiustoERifiutato() throws Exception {
		String codice = codiceInviato();
		for (int i = 0; i < CodiceService.MASSIMO_TENTATIVI; i++) {
			reset(EMAIL, sbagliato(codice), NUOVA_PASSWORD).andExpect(status().isBadRequest());
		}

		reset(EMAIL, codice, NUOVA_PASSWORD).andExpect(status().isBadRequest());

		assertThat(passwordEncoder.matches(PASSWORD, utenteDalDatabase().getPassword())).isTrue();
	}

	/**
	 * Ogni fallimento risponde allo stesso modo (status, codice e messaggio): gli errori distinti
	 * rivelerebbero che l'email è registrata.
	 */
	@Test
	void tuttiIFallimentiDannoLaStessaRisposta() throws Exception {
		// email inesistente
		ResultActions inesistente = reset("nessuno@example.com", "123456", NUOVA_PASSWORD);
		// utente registrato che non ha mai chiesto un codice
		ResultActions senzaCodice = reset(EMAIL, "123456", NUOVA_PASSWORD);
		// codice sbagliato
		String codice = codiceInviato();
		ResultActions sbagliato = reset(EMAIL, sbagliato(codice), NUOVA_PASSWORD);
		// tentativi esauriti (il quinto errore e i successivi)
		for (int i = 1; i < CodiceService.MASSIMO_TENTATIVI; i++) {
			reset(EMAIL, sbagliato(codice), NUOVA_PASSWORD);
		}
		ResultActions esauriti = reset(EMAIL, codice, NUOVA_PASSWORD);
		// codice scaduto (giusto, ma creato 20 minuti fa)
		codiceRepository.deleteAll();
		codiceRepository.save(new CodiceVerifica(utente, TipoCodice.RESET_PASSWORD, passwordEncoder.encode("123456"),
				null, Instant.now(clock).minus(Duration.ofMinutes(20))));
		ResultActions scaduto = reset(EMAIL, "123456", NUOVA_PASSWORD);

		for (ResultActions risposta : List.of(inesistente, senzaCodice, sbagliato, esauriti, scaduto)) {
			risposta.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codice").value("CODICE_NON_VALIDO"))
					.andExpect(jsonPath("$.messaggio").value("Codice non valido o scaduto: richiedine uno nuovo"));
		}
		assertThat(passwordEncoder.matches(PASSWORD, utenteDalDatabase().getPassword())).isTrue();
	}

	@Test
	void utenteCancellato400CodiceNonValido() throws Exception {
		String codice = codiceInviato();
		utente = utenteDalDatabase();
		utente.setDeletedAt(Instant.now(clock));
		utenteRepository.save(utente);

		reset(EMAIL, codice, NUOVA_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("CODICE_NON_VALIDO"));
	}

	@Test
	void nuovaPasswordDebole400Validazione() throws Exception {
		String codice = codiceInviato();

		reset(EMAIL, codice, "debole")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.nuovaPassword").exists());

		// la validazione viene prima: il codice non è stato toccato
		assertThat(codiciDalDatabase()).singleElement()
				.satisfies(salvato -> assertThat(salvato.getTentativiFalliti()).isZero());
	}

	@Test
	void campiVuoti400Validazione() throws Exception {
		reset(" ", " ", " ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.email").value("L'email è obbligatoria"))
				.andExpect(jsonPath("$.campi.codice").value("Il codice è obbligatorio"));
	}

	@Test
	void emailECodiceConSpaziVengonoPuliti() throws Exception {
		String codice = codiceInviato();

		reset("  MARIO@Example.com ", " " + codice + " ", NUOVA_PASSWORD).andExpect(status().isNoContent());

		assertThat(passwordEncoder.matches(NUOVA_PASSWORD, utenteDalDatabase().getPassword())).isTrue();
	}

}
