package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import it.quackfit.common.ClockConfig;
import it.quackfit.contenuti.Lezione;
import it.quackfit.contenuti.LezioneRepository;
import it.quackfit.contenuti.Tappa;
import it.quackfit.contenuti.TappaRepository;
import it.quackfit.security.JwtService;
import it.quackfit.tentativi.EsitoTentativo;
import it.quackfit.tentativi.TentativoLezione;
import it.quackfit.tentativi.TentativoLezioneRepository;

/**
 * Test di integrazione di GET /api/me con H2. Le lezioni e le tappe si puliscono con SQL diretto:
 * il loro deleteAll() farebbe solo un soft delete e le righe resterebbero nel database condiviso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeControllerTest {

	private static final Instant CREAZIONE = Instant.parse("2026-01-01T10:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private StreakRepository streakRepository;

	@Autowired
	private TentativoLezioneRepository tentativoRepository;

	@Autowired
	private TappaRepository tappaRepository;

	@Autowired
	private LezioneRepository lezioneRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock clock;

	private Utente mario;
	private Streak streak;
	private Tappa tappa;

	@BeforeEach
	void setUp() {
		pulisci();
		mario = new Utente("Mario", "Rossi", "mario@example.com", "hash", CREAZIONE);
		mario.setEmailVerificata(true);
		mario = utenteRepository.save(mario);
		streak = new Streak(mario, CREAZIONE);
		streak.setValore(3);
		streak.setRecord(7);
		streak.setUltimaAttivita(oggi());
		streak = streakRepository.save(streak);
		tappa = tappaRepository.save(new Tappa("Tappa", "Descrizione", 1, CREAZIONE));
	}

	@AfterEach
	void pulisci() {
		tentativoRepository.deleteAll();
		streakRepository.deleteAll();
		jdbc.update("delete from lezione");
		jdbc.update("delete from tappa");
		utenteRepository.deleteAll();
	}

	private LocalDate oggi() {
		return LocalDate.ofInstant(Instant.now(clock), ClockConfig.FUSO);
	}

	private Lezione lezione(int ordine) {
		return lezioneRepository.save(new Lezione(tappa, "Lezione " + ordine, "Descrizione", ordine, CREAZIONE));
	}

	private void tentativo(Utente utente, Lezione lezione, EsitoTentativo esito) {
		TentativoLezione tentativo = new TentativoLezione(utente, lezione, CREAZIONE);
		tentativo.setEsito(esito);
		tentativoRepository.save(tentativo);
	}

	private ResultActions profilo(Utente utente) throws Exception {
		return mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.genera(utente)));
	}

	@Test
	void datiDelProfilo() throws Exception {
		profilo(mario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("Mario"))
				.andExpect(jsonPath("$.cognome").value("Rossi"))
				.andExpect(jsonPath("$.email").value("mario@example.com"))
				.andExpect(jsonPath("$.ruolo").value("UTENTE"))
				.andExpect(jsonPath("$.fotoProfilo").isEmpty())
				.andExpect(jsonPath("$.emailVerificata").value(true))
				.andExpect(jsonPath("$.streak.valore").value(3))
				.andExpect(jsonPath("$.streak.record").value(7))
				.andExpect(jsonPath("$.lezioniCompletate").value(0))
				// niente id né dati riservati dell'utente
				.andExpect(jsonPath("$.id").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void streakScadutaMostraZeroESulDatabaseResta() throws Exception {
		streak.setUltimaAttivita(oggi().minusDays(2));
		streakRepository.save(streak);

		profilo(mario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.streak.valore").value(0))
				.andExpect(jsonPath("$.streak.record").value(7));

		Streak salvata = streakRepository.findById(streak.getId()).orElseThrow();
		assertThat(salvata.getValore()).isEqualTo(3);
		assertThat(salvata.getUltimaAttivita()).isEqualTo(oggi().minusDays(2));
	}

	@Test
	void lezioniCompletateSoloAttiveSuperateEUnaVoltaPerLezione() throws Exception {
		Utente luigi = utenteRepository.save(new Utente("Luigi", "Verdi", "luigi@example.com", "hash", CREAZIONE));
		Lezione superataDueVolte = lezione(1);
		Lezione superata = lezione(2);
		Lezione fallita = lezione(3);
		Lezione inCorso = lezione(4);
		Lezione superataPoiCancellata = lezione(5);
		Lezione superataDaLuigi = lezione(6);
		tentativo(mario, superataDueVolte, EsitoTentativo.SUPERATO);
		tentativo(mario, superataDueVolte, EsitoTentativo.SUPERATO);
		tentativo(mario, superata, EsitoTentativo.FALLITO);
		tentativo(mario, superata, EsitoTentativo.SUPERATO);
		tentativo(mario, fallita, EsitoTentativo.FALLITO);
		tentativo(mario, inCorso, EsitoTentativo.IN_CORSO);
		tentativo(mario, superataPoiCancellata, EsitoTentativo.SUPERATO);
		tentativo(luigi, superataDaLuigi, EsitoTentativo.SUPERATO);
		// soft delete come lo farebbe il service: cancella() del repository vuole una transazione aperta
		jdbc.update("update lezione set deleted_at = ? where id = ?", Timestamp.from(Instant.now(clock)),
				superataPoiCancellata.getId());

		profilo(mario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lezioniCompletate").value(2));
	}

	@Test
	void funzionaAncheConEmailNonVerificata() throws Exception {
		mario.setEmailVerificata(false);
		mario = utenteRepository.save(mario);

		profilo(mario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailVerificata").value(false));
	}

	@Test
	void senzaToken401() throws Exception {
		mockMvc.perform(get("/api/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codice").value("NON_AUTENTICATO"));
	}

	@Test
	void streakMancante500ConMessaggioGenerico() throws Exception {
		streakRepository.deleteAll();

		profilo(mario)
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.codice").value("ERRORE_INTERNO"))
				.andExpect(jsonPath("$.errore").value("Internal Server Error"))
				.andExpect(jsonPath("$.messaggio").value("Si è verificato un errore imprevisto, riprova più tardi"))
				.andExpect(jsonPath("$.campi").value(anEmptyMap()))
				.andExpect(jsonPath("$.timestamp").exists())
				// nessun dettaglio interno: né l'id dell'utente né il nome dell'eccezione
				.andExpect(content().string(not(containsString(mario.getId().toString()))))
				.andExpect(content().string(not(containsString("Streak"))));
	}

}
