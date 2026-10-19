package it.quackfit.contenuti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import com.jayway.jsonpath.JsonPath;

import it.quackfit.security.JwtService;
import it.quackfit.tentativi.EsitoTentativo;
import it.quackfit.tentativi.TentativoLezione;
import it.quackfit.tentativi.TentativoLezioneRepository;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/**
 * Test di integrazione della mappa con H2 (percorso.md, Stato delle lezioni e Mappa). Le regole del calcolo una per
 * una sono in PercorsoServiceTest; qui il flusso con il database: tentativi, cancellazioni, forma della risposta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PercorsoControllerTest {

	private static final Instant CREAZIONE = Instant.parse("2026-01-01T10:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private TappaRepository tappaRepository;

	@Autowired
	private LezioneRepository lezioneRepository;

	@Autowired
	private DomandaRepository domandaRepository;

	@Autowired
	private TentativoLezioneRepository tentativoRepository;

	@Autowired
	private JdbcTemplate jdbc;

	private Utente mario;

	@BeforeEach
	void setUp() {
		pulisci();
		mario = utente("mario@example.com", true);
	}

	/** Prima i tentativi (puntano a utente e lezione), poi i contenuti con SQL diretto (soft delete), poi gli utenti. */
	@AfterEach
	void pulisci() {
		tentativoRepository.deleteAll();
		jdbc.update("update elemento_abbinamento set abbinamento_corretto_id = null");
		jdbc.update("delete from elemento_abbinamento");
		jdbc.update("delete from opzione");
		jdbc.update("delete from domanda");
		jdbc.update("delete from lezione");
		jdbc.update("delete from tappa");
		utenteRepository.deleteAll();
	}

	private Utente utente(String email, boolean verificata) {
		Utente utente = new Utente("Mario", "Rossi", email, "hash", CREAZIONE);
		utente.setEmailVerificata(verificata);
		return utenteRepository.save(utente);
	}

	private Tappa tappa(String titolo, int ordine) {
		return tappaRepository.save(new Tappa(titolo, "Descrizione " + titolo, ordine, CREAZIONE));
	}

	private Lezione lezione(Tappa tappa, String titolo, int ordine) {
		return lezioneRepository.save(new Lezione(tappa, titolo, "Descrizione " + titolo, ordine, CREAZIONE));
	}

	private void tentativo(Utente utente, Lezione lezione, EsitoTentativo esito) {
		TentativoLezione tentativo = new TentativoLezione(utente, lezione, CREAZIONE);
		tentativo.setEsito(esito);
		tentativoRepository.save(tentativo);
	}

	private void cancella(String tabella, Object id) {
		jdbc.update("update " + tabella + " set deleted_at = ? where id = ?", Timestamp.from(CREAZIONE), id);
	}

	private Domanda veroFalso(Lezione lezione, int ordine) {
		Domanda domanda = new Domanda(lezione, "Vero o falso " + ordine, "Spiegazione segreta", TipoDomanda.VERO_FALSO,
				ordine, CREAZIONE);
		domanda.aggiungiOpzione("Vero", false);
		domanda.aggiungiOpzione("Falso", true);
		return domandaRepository.save(domanda);
	}

	private ResultActions lezione(Utente utente, Object id) throws Exception {
		return mockMvc.perform(get("/api/path/lessons/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.genera(utente)));
	}

	private ResultActions mappa(Utente utente) throws Exception {
		return mockMvc.perform(get("/api/path/stages").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.genera(utente)));
	}

	@Test
	void nuovoUtenteFormaDellaRispostaESoloLaPrimaSbloccata() throws Exception {
		Tappa prima = tappa("Prima", 1);
		Lezione l1 = lezione(prima, "L1", 1);
		lezione(prima, "L2", 2);
		Tappa seconda = tappa("Seconda", 2);
		lezione(seconda, "L3", 1);

		mappa(mario)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].titolo", contains("Prima", "Seconda")))
				.andExpect(jsonPath("$[0].id").value(prima.getId().toString()))
				.andExpect(jsonPath("$[0].descrizione").value("Descrizione Prima"))
				.andExpect(jsonPath("$[0].ordine").value(1))
				.andExpect(jsonPath("$[0].lezioni[0].id").value(l1.getId().toString()))
				.andExpect(jsonPath("$[0].lezioni[0].titolo").value("L1"))
				.andExpect(jsonPath("$[0].lezioni[0].descrizione").value("Descrizione L1"))
				.andExpect(jsonPath("$[0].lezioni[0].ordine").value(1))
				.andExpect(jsonPath("$[0].lezioni[*].stato", contains("SBLOCCATA", "BLOCCATA")))
				.andExpect(jsonPath("$[1].lezioni[*].stato", contains("BLOCCATA")))
				// nessun numero di domande né domande
				.andExpect(jsonPath("$[0].lezioni[0].numeroDomande").doesNotExist())
				.andExpect(jsonPath("$[0].lezioni[0].domande").doesNotExist());
	}

	@Test
	void ultimaLezioneDiUnaTappaSbloccaLaPrimaDellaSuccessiva() throws Exception {
		Tappa prima = tappa("Prima", 1);
		Lezione l1 = lezione(prima, "L1", 1);
		Lezione l2 = lezione(prima, "L2", 2);
		Tappa seconda = tappa("Seconda", 2);
		lezione(seconda, "L3", 1);
		lezione(seconda, "L4", 2);
		tentativo(mario, l1, EsitoTentativo.SUPERATO);
		tentativo(mario, l2, EsitoTentativo.SUPERATO);

		mappa(mario)
				.andExpect(jsonPath("$[0].lezioni[*].stato", contains("COMPLETATA", "COMPLETATA")))
				.andExpect(jsonPath("$[1].lezioni[*].stato", contains("SBLOCCATA", "BLOCCATA")));
	}

	@Test
	void tentativoFallitoOInCorsoNonCompletaEUnaCompletataRestaTale() throws Exception {
		Tappa tappa = tappa("T", 1);
		Lezione l1 = lezione(tappa, "L1", 1);
		Lezione l2 = lezione(tappa, "L2", 2);
		lezione(tappa, "L3", 3);
		tentativo(mario, l1, EsitoTentativo.SUPERATO);
		// rifatta e fallita dopo: resta completata
		tentativo(mario, l1, EsitoTentativo.FALLITO);
		tentativo(mario, l2, EsitoTentativo.FALLITO);
		tentativo(mario, l2, EsitoTentativo.IN_CORSO);

		mappa(mario).andExpect(jsonPath("$[0].lezioni[*].stato", contains("COMPLETATA", "SBLOCCATA", "BLOCCATA")));
	}

	@Test
	void ogniUtenteVedeIlProprioStato() throws Exception {
		Tappa tappa = tappa("T", 1);
		Lezione l1 = lezione(tappa, "L1", 1);
		lezione(tappa, "L2", 2);
		Utente luigi = utente("luigi@example.com", true);
		tentativo(luigi, l1, EsitoTentativo.SUPERATO);

		mappa(mario).andExpect(jsonPath("$[0].lezioni[*].stato", contains("SBLOCCATA", "BLOCCATA")));
		mappa(luigi).andExpect(jsonPath("$[0].lezioni[*].stato", contains("COMPLETATA", "SBLOCCATA")));
	}

	@Test
	void lezioneCancellataAssenteENonContaNelloSblocco() throws Exception {
		Tappa tappa = tappa("T", 1);
		Lezione l1 = lezione(tappa, "L1", 1);
		Lezione l2 = lezione(tappa, "L2", 2);
		lezione(tappa, "L3", 3);
		tentativo(mario, l1, EsitoTentativo.SUPERATO);
		// L2 cancellata (senza rinumerare: conta l'ordine relativo): L3 viene subito dopo L1, che è completata
		cancella("lezione", l2.getId());

		mappa(mario)
				.andExpect(jsonPath("$[0].lezioni[*].titolo", contains("L1", "L3")))
				.andExpect(jsonPath("$[0].lezioni[*].stato", contains("COMPLETATA", "SBLOCCATA")));
	}

	@Test
	void lezioneCompletataPoiCancellataNonSbloccaPiuNulla() throws Exception {
		Tappa tappa = tappa("T", 1);
		Lezione l1 = lezione(tappa, "L1", 1);
		lezione(tappa, "L2", 2);
		tentativo(mario, l1, EsitoTentativo.SUPERATO);
		cancella("lezione", l1.getId());

		mappa(mario).andExpect(jsonPath("$[0].lezioni[*].stato", contains("SBLOCCATA")));
	}

	@Test
	void tappeSenzaLezioniAttiveOCancellateAssenti() throws Exception {
		tappa("Vuota", 1);
		Tappa conLezioneCancellata = tappa("Solo cancellate", 2);
		cancella("lezione", lezione(conLezioneCancellata, "Via", 1).getId());
		Tappa cancellata = tappa("Cancellata", 3);
		lezione(cancellata, "Orfana", 1);
		cancella("tappa", cancellata.getId());
		Tappa piena = tappa("Piena", 4);
		lezione(piena, "L1", 1);

		mappa(mario)
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].titolo").value("Piena"))
				.andExpect(jsonPath("$[0].lezioni[*].stato", contains("SBLOCCATA")));
	}

	@Test
	void lezioneSenzaDomandeCompareComeLeAltre() throws Exception {
		// regola operativa di percorso.md: nessuna esclusione automatica delle lezioni senza domande
		Tappa tappa = tappa("T", 1);
		lezione(tappa, "Senza domande", 1);

		mappa(mario).andExpect(jsonPath("$[0].lezioni[*].stato", contains("SBLOCCATA")));
	}

	@Test
	void percorsoVuotoListaVuota() throws Exception {
		mappa(mario).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void senzaTokenOEmailNonVerificata() throws Exception {
		mockMvc.perform(get("/api/path/stages"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codice").value("NON_AUTENTICATO"));
		mappa(utente("nonverificato@example.com", false))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("EMAIL_NON_VERIFICATA"));
	}

	// --- contenuto di una lezione ---

	@Test
	void lezioneInesistenteCancellataOSenzaDomande404() throws Exception {
		Tappa tappa = tappa("T", 1);
		Lezione cancellata = lezione(tappa, "Cancellata", 1);
		veroFalso(cancellata, 1);
		cancella("lezione", cancellata.getId());
		Lezione vuota = lezione(tappa, "Vuota", 2);
		Lezione soloCancellate = lezione(tappa, "Solo domande cancellate", 3);
		cancella("domanda", veroFalso(soloCancellate, 1).getId());

		for (Object id : new Object[] { UUID.randomUUID(), cancellata.getId(), vuota.getId(), soloCancellate.getId() }) {
			lezione(mario, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.codice").value("NON_TROVATO"));
		}
	}

	@Test
	void lezioneBloccata403EUnaCompletataSiRifa() throws Exception {
		Tappa tappa = tappa("T", 1);
		Lezione l1 = lezione(tappa, "L1", 1);
		veroFalso(l1, 1);
		Lezione l2 = lezione(tappa, "L2", 2);
		veroFalso(l2, 1);

		lezione(mario, l2.getId()).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("LEZIONE_BLOCCATA"));
		lezione(mario, l1.getId()).andExpect(status().isOk());

		tentativo(mario, l1, EsitoTentativo.SUPERATO);
		lezione(mario, l1.getId()).andExpect(status().isOk());
		lezione(mario, l2.getId()).andExpect(status().isOk());
	}

	@Test
	void contenutoConTuttiGliElementiESenzaSoluzioni() throws Exception {
		Tappa tappa = tappa("T", 1);
		Lezione l1 = lezione(tappa, "L1", 1);
		veroFalso(l1, 1);
		Domanda multipla = new Domanda(l1, "Quale?", "Spiegazione segreta", TipoDomanda.SCELTA_MULTIPLA, 2, CREAZIONE);
		multipla.aggiungiOpzione("A", true);
		multipla.aggiungiOpzione("B", false);
		multipla.aggiungiOpzione("C", false);
		domandaRepository.save(multipla);
		Domanda abbinamento = new Domanda(l1, "Abbina", "Spiegazione segreta", TipoDomanda.ABBINAMENTO, 3, CREAZIONE);
		abbinamento.aggiungiCoppia("Bicipite", "Braccio");
		abbinamento.aggiungiCoppia("Quadricipite", "Coscia");
		domandaRepository.save(abbinamento);
		cancella("domanda", veroFalso(l1, 4).getId());

		String json = lezione(mario, l1.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(l1.getId().toString()))
				.andExpect(jsonPath("$.titolo").value("L1"))
				.andExpect(jsonPath("$.descrizione").value("Descrizione L1"))
				.andExpect(jsonPath("$.domande", hasSize(3)))
				.andExpect(jsonPath("$.domande[*].testo", containsInAnyOrder("Vero o falso 1", "Quale?", "Abbina")))
				.andExpect(jsonPath("$.domande[?(@.tipo == 'VERO_FALSO')].opzioni[*].testo", contains("Vero", "Falso")))
				.andExpect(jsonPath("$.domande[?(@.tipo == 'SCELTA_MULTIPLA')].opzioni[*].testo",
						containsInAnyOrder("A", "B", "C")))
				.andExpect(jsonPath("$.domande[?(@.tipo == 'ABBINAMENTO')].sinistra[*].testo",
						containsInAnyOrder("Bicipite", "Quadricipite")))
				.andExpect(jsonPath("$.domande[?(@.tipo == 'ABBINAMENTO')].destra[*].testo",
						containsInAnyOrder("Braccio", "Coscia")))
				// i campi che non riguardano il tipo ci sono e sono []
				.andExpect(jsonPath("$.domande[?(@.tipo == 'VERO_FALSO')].sinistra[*]", hasSize(0)))
				.andExpect(jsonPath("$.domande[?(@.tipo == 'ABBINAMENTO')].opzioni[*]", hasSize(0)))
				.andReturn().getResponse().getContentAsString();

		// nessun campo della soluzione né la spiegazione, in nessun punto del JSON
		assertThat(json).doesNotContain("corretta", "rispostaVero", "abbinamentoCorretto", "spiegazione",
				"Spiegazione segreta");
	}

	@Test
	void abbinamentoLaPosizioneDiDestraNonRivelaLeCoppie() throws Exception {
		Tappa tappa = tappa("T", 1);
		Lezione l1 = lezione(tappa, "L1", 1);
		Domanda abbinamento = new Domanda(l1, "Abbina", "Spiegazione", TipoDomanda.ABBINAMENTO, 1, CREAZIONE);
		Map<String, String> coppie = new HashMap<>(Map.of("S1", "D1", "S2", "D2", "S3", "D3", "S4", "D4"));
		coppie.forEach(abbinamento::aggiungiCoppia);
		domandaRepository.save(abbinamento);

		// mescolamento casuale: su 20 richieste le posizioni non possono coincidere sempre con le coppie
		boolean sempreAllineate = true;
		for (int i = 0; i < 20; i++) {
			String json = lezione(mario, l1.getId()).andReturn().getResponse().getContentAsString();
			List<String> sinistra = JsonPath.read(json, "$.domande[0].sinistra[*].testo");
			List<String> destra = JsonPath.read(json, "$.domande[0].destra[*].testo");
			assertThat(destra).containsExactlyInAnyOrderElementsOf(coppie.values());
			if (!destra.equals(sinistra.stream().map(coppie::get).toList())) {
				sempreAllineate = false;
			}
		}
		assertThat(sempreAllineate).isFalse();
	}

	@Test
	void contenutoSenzaTokenOEmailNonVerificata() throws Exception {
		Tappa tappa = tappa("T", 1);
		Lezione l1 = lezione(tappa, "L1", 1);
		veroFalso(l1, 1);

		mockMvc.perform(get("/api/path/lessons/" + l1.getId())).andExpect(status().isUnauthorized());
		lezione(utente("nonverificato@example.com", false), l1.getId())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("EMAIL_NON_VERIFICATA"));
	}

}
