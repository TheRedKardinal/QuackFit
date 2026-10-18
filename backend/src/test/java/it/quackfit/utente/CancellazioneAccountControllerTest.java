package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import it.quackfit.auth.CodiceService;
import it.quackfit.auth.CodiceVerificaRepository;
import it.quackfit.auth.TipoCodice;
import it.quackfit.config.ArchivioImmagini;
import it.quackfit.config.CaricamentoImmagineFallitoException;
import it.quackfit.contenuti.Lezione;
import it.quackfit.contenuti.LezioneRepository;
import it.quackfit.contenuti.Tappa;
import it.quackfit.contenuti.TappaRepository;
import it.quackfit.tentativi.EsitoTentativo;
import it.quackfit.tentativi.TentativoLezione;
import it.quackfit.tentativi.TentativoLezioneRepository;

/**
 * Test di integrazione di DELETE /api/me con H2 e Cloudinary simulato. DELETE /api/me conta nel limite per IP:
 * ogni richiesta arriva da un IP diverso. Il token è emesso 10 secondi fa, così il logout ovunque lo rende davvero
 * non valido (iat confrontato al secondo, vedi auth.md, Note). Lezioni e tappe si puliscono con SQL diretto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CancellazioneAccountControllerTest {

	private static final String PASSWORD = "Password1!";
	private static final String EMAIL = "mario@example.com";
	private static final Instant CREAZIONE = Instant.parse("2026-01-01T10:00:00Z");
	private static final AtomicInteger PROSSIMO_IP = new AtomicInteger(1);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private StreakRepository streakRepository;

	@Autowired
	private CodiceVerificaRepository codiceRepository;

	@Autowired
	private CodiceService codiceService;

	@Autowired
	private TentativoLezioneRepository tentativoRepository;

	@Autowired
	private TappaRepository tappaRepository;

	@Autowired
	private LezioneRepository lezioneRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	@Value("${quackfit.jwt.segreto}")
	private String segreto;

	@MockitoBean
	private ArchivioImmagini archivio;

	private Utente mario;
	private String token;

	@BeforeEach
	void setUp() {
		pulisci();
		mario = new Utente("Mario", "Rossi", EMAIL, passwordEncoder.encode(PASSWORD), CREAZIONE);
		mario.setEmailVerificata(true);
		mario.setFotoProfilo("https://res.cloudinary.com/q/image/upload/v1/avatar/m.png");
		mario = utenteRepository.save(mario);
		streakRepository.save(new Streak(mario, CREAZIONE));
		token = tokenEmessoPrima(mario);
	}

	@AfterEach
	void pulisci() {
		tentativoRepository.deleteAll();
		codiceRepository.deleteAll();
		streakRepository.deleteAll();
		jdbc.update("delete from lezione");
		jdbc.update("delete from tappa");
		utenteRepository.deleteAll();
	}

	private String tokenEmessoPrima(Utente utente) {
		Instant adesso = Instant.now();
		return "Bearer " + Jwts.builder()
				.subject(utente.getId().toString())
				.issuedAt(Date.from(adesso.minusSeconds(10)))
				.expiration(Date.from(adesso.plusSeconds(3600)))
				.signWith(Keys.hmacShaKeyFor(segreto.getBytes(StandardCharsets.UTF_8)))
				.compact();
	}

	private static MockHttpServletRequestBuilder daIpNuovo(MockHttpServletRequestBuilder richiesta) {
		String ip = "10.60.0." + PROSSIMO_IP.getAndIncrement();
		return richiesta.with(r -> {
			r.setRemoteAddr(ip);
			return r;
		});
	}

	private ResultActions cancella(String password) throws Exception {
		return mockMvc.perform(daIpNuovo(delete("/api/me"))
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\": \"" + password + "\"}"));
	}

	private Utente marioDalDatabase() {
		return utenteRepository.findById(mario.getId()).orElseThrow();
	}

	private TentativoLezione tentativoSuperato() {
		Tappa tappa = tappaRepository.save(new Tappa("Tappa", "Descrizione", 1, CREAZIONE));
		Lezione lezione = lezioneRepository.save(new Lezione(tappa, "Lezione", "Descrizione", 1, CREAZIONE));
		TentativoLezione tentativo = new TentativoLezione(mario, lezione, CREAZIONE);
		tentativo.setEsito(EsitoTentativo.SUPERATO);
		return tentativoRepository.save(tentativo);
	}

	@Test
	void cancellazioneRiuscitaDatiAnonimizzati() throws Exception {
		cancella(PASSWORD).andExpect(status().isNoContent());

		Utente salvato = marioDalDatabase();
		assertThat(salvato.getNome()).isEqualTo("Utente");
		assertThat(salvato.getCognome()).isEqualTo("Eliminato");
		assertThat(salvato.getEmail()).isEqualTo("deleted-" + mario.getId() + "@quackfit.local");
		assertThat(passwordEncoder.matches(PASSWORD, salvato.getPassword())).isFalse();
		assertThat(salvato.getFotoProfilo()).isNull();
		assertThat(salvato.getDeletedAt()).isNotNull();
	}

	@Test
	void streakECodiciEliminatiTentativiConservati() throws Exception {
		codiceService.generaEInvia(mario, TipoCodice.VERIFICA_EMAIL, null);
		TentativoLezione tentativo = tentativoSuperato();

		cancella(PASSWORD).andExpect(status().isNoContent());

		assertThat(streakRepository.findByUtente(mario)).isEmpty();
		assertThat(codiceRepository.count()).isZero();
		TentativoLezione rimasto = tentativoRepository.findById(tentativo.getId()).orElseThrow();
		assertThat(rimasto.getEsito()).isEqualTo(EsitoTentativo.SUPERATO);
	}

	@Test
	void dopoLaCancellazioneTokenRifiutatoELoginImpossibile() throws Exception {
		cancella(PASSWORD).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(daIpNuovo(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"" + EMAIL + "\", \"password\": \"" + PASSWORD + "\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codice").value("CREDENZIALI_NON_VALIDE"));
	}

	@Test
	void lEmailTornaRegistrabile() throws Exception {
		cancella(PASSWORD).andExpect(status().isNoContent());

		mockMvc.perform(daIpNuovo(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\": \"Mario\", \"cognome\": \"Bianchi\", \"email\": \"" + EMAIL
						+ "\", \"password\": \"Nuova123!\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void fotoEliminataDaCloudinaryDopoLaCancellazione() throws Exception {
		cancella(PASSWORD).andExpect(status().isNoContent());

		verify(archivio).elimina("avatar/" + mario.getId());
	}

	@Test
	void seCloudinaryFallisceLaCancellazioneVaAvanti() throws Exception {
		doThrow(new CaricamentoImmagineFallitoException("Cloudinary non raggiungibile (ConnectException)"))
				.when(archivio).elimina(any());

		cancella(PASSWORD).andExpect(status().isNoContent());

		assertThat(marioDalDatabase().getDeletedAt()).isNotNull();
	}

	@Test
	void senzaFotoNessunaChiamataACloudinary() throws Exception {
		mario.setFotoProfilo(null);
		utenteRepository.save(mario);

		cancella(PASSWORD).andExpect(status().isNoContent());

		verify(archivio, never()).elimina(any());
	}

	@Test
	void passwordSbagliataNienteCambia() throws Exception {
		cancella("Sbagliata1!")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("PASSWORD_ATTUALE_ERRATA"));

		assertThat(marioDalDatabase().getDeletedAt()).isNull();
		assertThat(streakRepository.findByUtente(mario)).isPresent();
		verify(archivio, never()).elimina(any());
	}

	@Test
	void passwordMancante() throws Exception {
		mockMvc.perform(daIpNuovo(delete("/api/me"))
						.header(HttpHeaders.AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.password").value("La password è obbligatoria"));
	}

	@Test
	void ilSuperadminNonPuoCancellarsi() throws Exception {
		mario.setRuolo(Ruolo.SUPERADMIN);
		utenteRepository.save(mario);

		cancella(PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("ACCESSO_NEGATO"));
		assertThat(marioDalDatabase().getDeletedAt()).isNull();
	}

	@Test
	void conEmailNonVerificata403() throws Exception {
		mario.setEmailVerificata(false);
		utenteRepository.save(mario);

		cancella(PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("EMAIL_NON_VERIFICATA"));
	}

}
