package it.quackfit.tentativi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import it.quackfit.contenuti.Domanda;
import it.quackfit.contenuti.DomandaRepository;
import it.quackfit.contenuti.Lezione;
import it.quackfit.contenuti.LezioneRepository;
import it.quackfit.contenuti.Tappa;
import it.quackfit.contenuti.TappaRepository;
import it.quackfit.contenuti.TipoDomanda;
import it.quackfit.security.JwtService;
import it.quackfit.utente.Streak;
import it.quackfit.utente.StreakRepository;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/**
 * Test di integrazione di avvio, risposte, chiusura e resoconto con H2 (tentativi.md, Regole generali, Avvio,
 * Risposta, Chiusura, Resoconto). I lock su tentativo e streak non si provano qui (su H2 non dimostrano la concorrenza): si verificano a mano
 * su PostgreSQL. Tutti i casi della streak sono negli unit test di {@link TentativiService#aggiornaStreak}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TentativiControllerTest {

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
	private StreakRepository streakRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock clock;

	private Utente mario;
	private Tappa tappa;
	private Lezione lezione;
	private Domanda veroFalso;
	private Domanda multipla;
	private Domanda abbinamento;

	@BeforeEach
	void setUp() {
		pulisci();
		mario = utente("mario@example.com");
		tappa = tappaRepository.save(new Tappa("Tappa", "Descrizione", 1, CREAZIONE));
		lezione = lezioneRepository.save(new Lezione(tappa, "Lezione", "Descrizione", 1, CREAZIONE));
		veroFalso = new Domanda(lezione, "Vero o falso?", "Perché è falso", TipoDomanda.VERO_FALSO, 1, CREAZIONE);
		veroFalso.aggiungiOpzione("Vero", false);
		veroFalso.aggiungiOpzione("Falso", true);
		veroFalso = domandaRepository.save(veroFalso);
		multipla = new Domanda(lezione, "Quale?", "Perché B", TipoDomanda.SCELTA_MULTIPLA, 2, CREAZIONE);
		multipla.aggiungiOpzione("A", false);
		multipla.aggiungiOpzione("B", true);
		multipla.aggiungiOpzione("C", false);
		multipla = domandaRepository.save(multipla);
		abbinamento = new Domanda(lezione, "Abbina", "Così", TipoDomanda.ABBINAMENTO, 3, CREAZIONE);
		abbinamento.aggiungiCoppia("Bicipite", "Braccio");
		abbinamento.aggiungiCoppia("Quadricipite", "Coscia");
		abbinamento = domandaRepository.save(abbinamento);
	}

	/** Prima risposte e tentativi, poi i contenuti con SQL diretto (soft delete), infine streak e utenti. */
	@AfterEach
	void pulisci() {
		jdbc.update("delete from risposta_abbinamento");
		jdbc.update("delete from risposta_opzione");
		jdbc.update("delete from tentativo_lezione");
		jdbc.update("update elemento_abbinamento set abbinamento_corretto_id = null");
		jdbc.update("delete from elemento_abbinamento");
		jdbc.update("delete from opzione");
		jdbc.update("delete from domanda");
		jdbc.update("delete from lezione");
		jdbc.update("delete from tappa");
		jdbc.update("delete from streak");
		utenteRepository.deleteAll();
	}

	// --- dati e richieste ---

	private Utente utente(String email) {
		Utente utente = new Utente("Mario", "Rossi", email, "hash", CREAZIONE);
		utente.setEmailVerificata(true);
		return utenteRepository.save(utente);
	}

	private static UUID opzione(Domanda domanda, String testo) {
		return domanda.getOpzioni().stream().filter(o -> o.getTesto().equals(testo)).findFirst().orElseThrow().getId();
	}

	private static UUID elemento(Domanda domanda, String testo) {
		return domanda.getElementi().stream().filter(e -> e.getTesto().equals(testo)).findFirst().orElseThrow().getId();
	}

	private static String coppia(Domanda domanda, String sinistra, String destra) {
		return "{\"sinistroId\": \"" + elemento(domanda, sinistra) + "\", \"destroId\": \"" + elemento(domanda, destra)
				+ "\"}";
	}

	private ResultActions avvio(Utente utente, String json) throws Exception {
		return mockMvc.perform(post("/api/attempts").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.genera(utente))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private UUID avvia(Lezione daAvviare) throws Exception {
		String json = avvio(mario, "{\"lezioneId\": \"" + daAvviare.getId() + "\"}")
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(json, "$.tentativoId"));
	}

	private ResultActions risposta(Utente utente, UUID tentativo, String json) throws Exception {
		return mockMvc.perform(post("/api/attempts/" + tentativo + "/answers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.genera(utente))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions conOpzione(UUID tentativo, Domanda domanda, UUID opzioneId) throws Exception {
		return risposta(mario, tentativo,
				"{\"domandaId\": \"" + domanda.getId() + "\", \"opzioneId\": \"" + opzioneId + "\"}");
	}

	private ResultActions conCoppie(UUID tentativo, Domanda domanda, String... coppie) throws Exception {
		return risposta(mario, tentativo,
				"{\"domandaId\": \"" + domanda.getId() + "\", \"coppie\": [" + String.join(", ", coppie) + "]}");
	}

	private TentativoLezione tentativo(UUID id) {
		return tentativoRepository.findById(id).orElseThrow();
	}

	private int righe(String tabella, UUID tentativo) {
		return jdbc.queryForObject("select count(*) from " + tabella + " where tentativo_id = ?", Integer.class,
				tentativo);
	}

	private void cancella(String tabella, UUID id) {
		jdbc.update("update " + tabella + " set deleted_at = ? where id = ?", Timestamp.from(CREAZIONE), id);
	}

	private ResultActions chiusura(Utente utente, UUID tentativo) throws Exception {
		return mockMvc.perform(post("/api/attempts/" + tentativo + "/complete")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.genera(utente)));
	}

	/** Streak di mario con valore, record e ultima attività dati. */
	private Streak streak(int valore, int record, LocalDate ultimaAttivita) {
		Streak streak = new Streak(mario, CREAZIONE);
		streak.setValore(valore);
		streak.setRecord(record);
		streak.setUltimaAttivita(ultimaAttivita);
		return streakRepository.save(streak);
	}

	private Streak streakDiMario() {
		return streakRepository.findByUtente(mario).orElseThrow();
	}

	private ResultActions resoconto(Utente utente, UUID tentativo) throws Exception {
		return mockMvc.perform(get("/api/attempts/" + tentativo)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.genera(utente)));
	}

	private void rispondiGiustoATutte(UUID id) throws Exception {
		conOpzione(id, veroFalso, opzione(veroFalso, "Falso")).andExpect(jsonPath("$.corretta").value(true));
		conOpzione(id, multipla, opzione(multipla, "B")).andExpect(jsonPath("$.corretta").value(true));
		conCoppie(id, abbinamento, coppia(abbinamento, "Bicipite", "Braccio"),
				coppia(abbinamento, "Quadricipite", "Coscia")).andExpect(jsonPath("$.corretta").value(true));
	}

	// --- avvio ---

	@Test
	void avvioDiUnaLezioneSbloccata() throws Exception {
		UUID id = avvia(lezione);

		TentativoLezione creato = tentativo(id);
		assertThat(creato.getEsito()).isEqualTo(EsitoTentativo.IN_CORSO);
		assertThat(creato.getErrori()).isZero();
		assertThat(creato.getFinitoAt()).isNull();
	}

	@Test
	void avvioBloccataInesistenteSenzaDomandeOSenzaLezione() throws Exception {
		Lezione seconda = lezioneRepository.save(new Lezione(tappa, "Seconda", "D", 2, CREAZIONE));
		Domanda vf = new Domanda(seconda, "Vf", "S", TipoDomanda.VERO_FALSO, 1, CREAZIONE);
		vf.aggiungiOpzione("Vero", true);
		vf.aggiungiOpzione("Falso", false);
		domandaRepository.save(vf);
		Lezione vuota = lezioneRepository.save(new Lezione(tappa, "Vuota", "D", 3, CREAZIONE));

		avvio(mario, "{\"lezioneId\": \"" + seconda.getId() + "\"}").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("LEZIONE_BLOCCATA"));
		avvio(mario, "{\"lezioneId\": \"" + UUID.randomUUID() + "\"}").andExpect(status().isNotFound());
		avvio(mario, "{\"lezioneId\": \"" + vuota.getId() + "\"}").andExpect(status().isNotFound());
		avvio(mario, "{}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.lezioneId").value("La lezione è obbligatoria"));
	}

	@Test
	void avvioChiudeSoloGliInCorsoDelloStessoUtenteSullaStessaLezione() throws Exception {
		Lezione altraLezione = lezioneRepository.save(new Lezione(tappa, "Altra", "D", 2, CREAZIONE));
		TentativoLezione suAltraLezione = new TentativoLezione(mario, altraLezione, CREAZIONE);
		tentativoRepository.save(suAltraLezione);
		Utente luigi = utente("luigi@example.com");
		TentativoLezione diLuigi = tentativoRepository.save(new TentativoLezione(luigi, lezione, CREAZIONE));
		UUID primo = avvia(lezione);

		UUID secondo = avvia(lezione);

		assertThat(tentativo(primo).getEsito()).isEqualTo(EsitoTentativo.FALLITO);
		assertThat(tentativo(primo).getFinitoAt()).isNotNull();
		assertThat(tentativo(secondo).getEsito()).isEqualTo(EsitoTentativo.IN_CORSO);
		assertThat(tentativo(suAltraLezione.getId()).getEsito()).isEqualTo(EsitoTentativo.IN_CORSO);
		assertThat(tentativo(diLuigi.getId()).getEsito()).isEqualTo(EsitoTentativo.IN_CORSO);
	}

	@Test
	void unaLezioneCompletataSiRifa() throws Exception {
		TentativoLezione superato = new TentativoLezione(mario, lezione, CREAZIONE);
		superato.setEsito(EsitoTentativo.SUPERATO);
		tentativoRepository.save(superato);

		avvia(lezione);
	}

	// --- risposta: valutazione e forma della soluzione ---

	@Test
	void veroFalsoGiustaConLaSoluzioneDelSuoTipo() throws Exception {
		UUID id = avvia(lezione);

		conOpzione(id, veroFalso, opzione(veroFalso, "Falso"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.corretta").value(true))
				.andExpect(jsonPath("$.soluzione.opzioneId").value(opzione(veroFalso, "Falso").toString()))
				.andExpect(jsonPath("$.soluzione.coppie", hasSize(0)))
				.andExpect(jsonPath("$.spiegazione").value("Perché è falso"))
				.andExpect(jsonPath("$.errori").value(0))
				.andExpect(jsonPath("$.esito").value("IN_CORSO"));
		assertThat(jdbc.queryForObject("select risolta from risposta_opzione where tentativo_id = ?", Boolean.class, id))
				.isTrue();
	}

	@Test
	void sceltaMultiplaSbagliataUnErroreESoluzione() throws Exception {
		UUID id = avvia(lezione);

		conOpzione(id, multipla, opzione(multipla, "C"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.corretta").value(false))
				.andExpect(jsonPath("$.soluzione.opzioneId").value(opzione(multipla, "B").toString()))
				.andExpect(jsonPath("$.soluzione.coppie", hasSize(0)))
				.andExpect(jsonPath("$.errori").value(1))
				.andExpect(jsonPath("$.esito").value("IN_CORSO"));
		assertThat(tentativo(id).getErrori()).isEqualTo(1);
	}

	@Test
	void abbinamentoGiustoConLaSoluzioneDelSuoTipo() throws Exception {
		UUID id = avvia(lezione);

		conCoppie(id, abbinamento, coppia(abbinamento, "Bicipite", "Braccio"),
				coppia(abbinamento, "Quadricipite", "Coscia"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.corretta").value(true))
				.andExpect(jsonPath("$.soluzione.opzioneId").value(nullValue()))
				.andExpect(jsonPath("$.soluzione.coppie", hasSize(2)))
				.andExpect(jsonPath("$.soluzione.coppie[*].destroId", containsInAnyOrder(
						elemento(abbinamento, "Braccio").toString(), elemento(abbinamento, "Coscia").toString())))
				.andExpect(jsonPath("$.errori").value(0));
		assertThat(righe("risposta_abbinamento", id)).isEqualTo(2);
	}

	@Test
	void abbinamentoUnErrorePerOgniCoppiaSbagliataEMassimoTre() throws Exception {
		UUID id = avvia(lezione);
		conOpzione(id, multipla, opzione(multipla, "A")).andExpect(jsonPath("$.errori").value(1));

		// due coppie sbagliate: 1 + 2 = 3 → FALLITO
		conCoppie(id, abbinamento, coppia(abbinamento, "Bicipite", "Coscia"),
				coppia(abbinamento, "Quadricipite", "Braccio"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.corretta").value(false))
				.andExpect(jsonPath("$.errori").value(3))
				.andExpect(jsonPath("$.esito").value("FALLITO"));
		assertThat(tentativo(id).getFinitoAt()).isNotNull();
	}

	@Test
	void gliErroriNonSuperanoIlMassimo() throws Exception {
		UUID id = avvia(lezione);
		conOpzione(id, multipla, opzione(multipla, "A"));
		conOpzione(id, multipla, opzione(multipla, "C")).andExpect(jsonPath("$.errori").value(2));

		// 2 + 2 coppie sbagliate = 4, ma il massimo è 3
		conCoppie(id, abbinamento, coppia(abbinamento, "Bicipite", "Coscia"),
				coppia(abbinamento, "Quadricipite", "Braccio"))
				.andExpect(jsonPath("$.errori").value(3))
				.andExpect(jsonPath("$.esito").value("FALLITO"));
	}

	@Test
	void terzoErroreFallitoEPoiTentativoConcluso409() throws Exception {
		UUID id = avvia(lezione);
		conOpzione(id, veroFalso, opzione(veroFalso, "Vero")).andExpect(jsonPath("$.errori").value(1));
		conOpzione(id, multipla, opzione(multipla, "A")).andExpect(jsonPath("$.errori").value(2));
		conOpzione(id, multipla, opzione(multipla, "C"))
				.andExpect(jsonPath("$.errori").value(3))
				.andExpect(jsonPath("$.esito").value("FALLITO"));

		conOpzione(id, veroFalso, opzione(veroFalso, "Falso"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codice").value("TENTATIVO_CHIUSO"))
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_TENTATIVO_CHIUSO));
	}

	// --- domande ripetute e doppio invio ---

	@Test
	void ripetizioneSalvaSoloLaPrimaContaGliErroriEPoiRisolve() throws Exception {
		UUID id = avvia(lezione);
		conOpzione(id, multipla, opzione(multipla, "A")).andExpect(jsonPath("$.errori").value(1));
		// ripetuta e ancora sbagliata: errore contato, risolta resta false
		conOpzione(id, multipla, opzione(multipla, "C")).andExpect(jsonPath("$.errori").value(2));
		assertThat(jdbc.queryForObject("select risolta from risposta_opzione where tentativo_id = ?", Boolean.class, id))
				.isFalse();

		// ripetuta e giusta: risolta = true, la prima risposta salvata non cambia
		conOpzione(id, multipla, opzione(multipla, "B")).andExpect(jsonPath("$.corretta").value(true))
				.andExpect(jsonPath("$.errori").value(2));
		assertThat(righe("risposta_opzione", id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select risolta from risposta_opzione where tentativo_id = ?", Boolean.class, id))
				.isTrue();
		assertThat(jdbc.queryForObject("select opzione_id from risposta_opzione where tentativo_id = ?", UUID.class, id))
				.isEqualTo(opzione(multipla, "A"));

		// già risolta → 400
		conOpzione(id, multipla, opzione(multipla, "B"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.domandaId").value(TentativiService.MSG_GIA_RISOLTA));
	}

	@Test
	void abbinamentoRipetutoGiustoRisolveTutteLeRighe() throws Exception {
		UUID id = avvia(lezione);
		conCoppie(id, abbinamento, coppia(abbinamento, "Bicipite", "Coscia"),
				coppia(abbinamento, "Quadricipite", "Braccio")).andExpect(jsonPath("$.errori").value(2));

		conCoppie(id, abbinamento, coppia(abbinamento, "Bicipite", "Braccio"),
				coppia(abbinamento, "Quadricipite", "Coscia")).andExpect(jsonPath("$.corretta").value(true));

		assertThat(righe("risposta_abbinamento", id)).isEqualTo(2);
		assertThat(jdbc.queryForList("select risolta from risposta_abbinamento where tentativo_id = ?", Boolean.class, id))
				.containsOnly(true);
		conCoppie(id, abbinamento, coppia(abbinamento, "Bicipite", "Braccio"),
				coppia(abbinamento, "Quadricipite", "Coscia"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.domandaId").value(TentativiService.MSG_GIA_RISOLTA));
	}

	@Test
	void doppioInvioGiustoUnaSolaRispostaNessunErroreNessun500() throws Exception {
		UUID id = avvia(lezione);

		conOpzione(id, veroFalso, opzione(veroFalso, "Falso")).andExpect(status().isOk());
		conOpzione(id, veroFalso, opzione(veroFalso, "Falso"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.domandaId").value(TentativiService.MSG_GIA_RISOLTA));

		assertThat(tentativo(id).getErrori()).isZero();
		assertThat(righe("risposta_opzione", id)).isEqualTo(1);
	}

	@Test
	void doppioInvioSbagliatoContaDueErroriMaSalvaUnaSolaRisposta() throws Exception {
		// limite noto (tentativi.md): per il backend è una ripetizione voluta; il doppio tap lo blocca il frontend
		UUID id = avvia(lezione);

		conOpzione(id, veroFalso, opzione(veroFalso, "Vero")).andExpect(status().isOk());
		conOpzione(id, veroFalso, opzione(veroFalso, "Vero")).andExpect(status().isOk());

		assertThat(tentativo(id).getErrori()).isEqualTo(2);
		assertThat(righe("risposta_opzione", id)).isEqualTo(1);
	}

	// --- tabella degli errori (una prova per riga) ---

	@Test
	void tentativoInesistenteOAltrui404ConLoStessoMessaggio() throws Exception {
		UUID id = avvia(lezione);
		Utente luigi = utente("luigi@example.com");
		String corpo = "{\"domandaId\": \"" + veroFalso.getId() + "\", \"opzioneId\": \"" + opzione(veroFalso, "Falso")
				+ "\"}";

		risposta(mario, UUID.randomUUID(), corpo).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_TENTATIVO_NON_TROVATO));
		risposta(luigi, id, corpo).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_TENTATIVO_NON_TROVATO));
	}

	@Test
	void domandaDiUnAltraLezioneOInesistente400() throws Exception {
		Lezione altra = lezioneRepository.save(new Lezione(tappa, "Altra", "D", 2, CREAZIONE));
		Domanda fuori = new Domanda(altra, "Fuori", "S", TipoDomanda.VERO_FALSO, 1, CREAZIONE);
		fuori.aggiungiOpzione("Vero", true);
		fuori.aggiungiOpzione("Falso", false);
		fuori = domandaRepository.save(fuori);
		UUID id = avvia(lezione);

		conOpzione(id, fuori, opzione(fuori, "Vero")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.domandaId").value(TentativiService.MSG_DOMANDA_ALTRA_LEZIONE));
		risposta(mario, id, "{\"domandaId\": \"" + UUID.randomUUID() + "\", \"opzioneId\": \"" + UUID.randomUUID() + "\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.domandaId").value(TentativiService.MSG_DOMANDA_ALTRA_LEZIONE));
		risposta(mario, id, "{}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.domandaId").value("La domanda è obbligatoria"));
	}

	@Test
	void domandaAggiuntaDopoLAvvio400ECancellata404() throws Exception {
		UUID id = avvia(lezione);
		Domanda nuova = new Domanda(lezione, "Nuova", "S", TipoDomanda.VERO_FALSO, 4, Instant.now().plusSeconds(3600));
		nuova.aggiungiOpzione("Vero", true);
		nuova.aggiungiOpzione("Falso", false);
		nuova = domandaRepository.save(nuova);
		cancella("domanda", multipla.getId());

		conOpzione(id, nuova, opzione(nuova, "Vero")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.domandaId").value(TentativiService.MSG_DOMANDA_AGGIUNTA));
		conOpzione(id, multipla, opzione(multipla, "B")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codice").value("NON_TROVATO"));
	}

	@Test
	void tutteLeDomandeDelTentativoCancellate404() throws Exception {
		UUID id = avvia(lezione);
		// resta solo una domanda aggiunta dopo l'avvio: per il tentativo non c'è più nulla
		Domanda nuova = new Domanda(lezione, "Nuova", "S", TipoDomanda.VERO_FALSO, 4, Instant.now().plusSeconds(3600));
		nuova.aggiungiOpzione("Vero", true);
		nuova.aggiungiOpzione("Falso", false);
		domandaRepository.save(nuova);
		for (Domanda d : List.of(veroFalso, multipla, abbinamento)) {
			cancella("domanda", d.getId());
		}

		conOpzione(id, veroFalso, opzione(veroFalso, "Falso")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_LEZIONE_NON_TROVATA));
	}

	@Test
	void lezioneCancellataDuranteIlTentativo404() throws Exception {
		UUID id = avvia(lezione);
		cancella("lezione", lezione.getId());

		conOpzione(id, veroFalso, opzione(veroFalso, "Falso")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_LEZIONE_NON_TROVATA));
	}

	@Test
	void campoSbagliatoPerIlTipo400() throws Exception {
		UUID id = avvia(lezione);
		String coppieAbbinamento = "[" + coppia(abbinamento, "Bicipite", "Braccio") + ", "
				+ coppia(abbinamento, "Quadricipite", "Coscia") + "]";

		risposta(mario, id, "{\"domandaId\": \"" + veroFalso.getId() + "\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.opzioneId").value(TentativiService.MSG_OPZIONE_OBBLIGATORIA));
		risposta(mario, id, "{\"domandaId\": \"" + veroFalso.getId() + "\", \"opzioneId\": \""
				+ opzione(veroFalso, "Falso") + "\", \"coppie\": " + coppieAbbinamento + "}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.coppie").value(TentativiService.MSG_COPPIE_NON_PREVISTE));
		risposta(mario, id, "{\"domandaId\": \"" + abbinamento.getId() + "\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.coppie").value(TentativiService.MSG_COPPIE_OBBLIGATORIE));
		risposta(mario, id, "{\"domandaId\": \"" + abbinamento.getId() + "\", \"opzioneId\": \""
				+ opzione(veroFalso, "Falso") + "\", \"coppie\": " + coppieAbbinamento + "}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.opzioneId").value(TentativiService.MSG_OPZIONE_NON_PREVISTA));
		assertThat(tentativo(id).getErrori()).isZero();
	}

	@Test
	void opzioneDiUnAltraDomandaOInesistente400ConLoStessoMessaggio() throws Exception {
		UUID id = avvia(lezione);

		conOpzione(id, multipla, opzione(veroFalso, "Vero")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.opzioneId").value(TentativiService.MSG_OPZIONE_ALTRA_DOMANDA));
		conOpzione(id, multipla, UUID.randomUUID()).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.opzioneId").value(TentativiService.MSG_OPZIONE_ALTRA_DOMANDA));
		assertThat(tentativo(id).getErrori()).isZero();
	}

	@Test
	void coppieMalformate400MaUnAbbinamentoSbagliatoBenFormatoE200() throws Exception {
		UUID id = avvia(lezione);
		Domanda altro = new Domanda(lezione, "Altro", "S", TipoDomanda.ABBINAMENTO, 4, CREAZIONE);
		altro.aggiungiCoppia("Gluteo", "Anca");
		altro.aggiungiCoppia("Dorsale", "Schiena");
		altro = domandaRepository.save(altro);
		String estranea = "{\"sinistroId\": \"" + elemento(altro, "Gluteo") + "\", \"destroId\": \""
				+ elemento(altro, "Anca") + "\"}";

		// incompleta, sinistro ripetuto, destro ripetuto, elemento di un'altra domanda
		for (String[] coppie : List.of(
				new String[] { coppia(abbinamento, "Bicipite", "Braccio") },
				new String[] { coppia(abbinamento, "Bicipite", "Braccio"), coppia(abbinamento, "Bicipite", "Coscia") },
				new String[] { coppia(abbinamento, "Bicipite", "Braccio"), coppia(abbinamento, "Quadricipite", "Braccio") },
				new String[] { coppia(abbinamento, "Bicipite", "Braccio"), estranea })) {
			conCoppie(id, abbinamento, coppie).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.campi.coppie").value(TentativiService.MSG_COPPIE_NON_VALIDE));
		}
		assertThat(tentativo(id).getErrori()).isZero();

		conCoppie(id, abbinamento, coppia(abbinamento, "Bicipite", "Coscia"),
				coppia(abbinamento, "Quadricipite", "Braccio")).andExpect(status().isOk())
				.andExpect(jsonPath("$.corretta").value(false));
	}

	// --- chiusura ---

	@Test
	void chiusuraSuperataAggiornaLaStreakESbloccaLaLezioneSuccessiva() throws Exception {
		LocalDate oggi = LocalDate.now(clock);
		streak(2, 2, oggi.minusDays(1));
		Lezione seconda = lezioneRepository.save(new Lezione(tappa, "Seconda", "D", 2, CREAZIONE));
		Domanda vf = new Domanda(seconda, "Vf", "S", TipoDomanda.VERO_FALSO, 1, CREAZIONE);
		vf.aggiungiOpzione("Vero", true);
		vf.aggiungiOpzione("Falso", false);
		domandaRepository.save(vf);
		UUID id = avvia(lezione);
		conOpzione(id, multipla, opzione(multipla, "A")).andExpect(jsonPath("$.errori").value(1));
		rispondiGiustoATutte(id);

		chiusura(mario, id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.esito").value("SUPERATO"))
				.andExpect(jsonPath("$.errori").value(1))
				.andExpect(jsonPath("$.streak.valore").value(3))
				.andExpect(jsonPath("$.streak.record").value(3));

		assertThat(tentativo(id).getEsito()).isEqualTo(EsitoTentativo.SUPERATO);
		assertThat(tentativo(id).getFinitoAt()).isNotNull();
		assertThat(streakDiMario().getValore()).isEqualTo(3);
		assertThat(streakDiMario().getUltimaAttivita()).isEqualTo(oggi);
		mockMvc.perform(get("/api/path/stages").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.genera(mario)))
				.andExpect(jsonPath("$[0].lezioni[0].stato").value("COMPLETATA"))
				.andExpect(jsonPath("$[0].lezioni[1].stato").value("SBLOCCATA"));
	}

	@Test
	void chiusuraConDomandeNonRisolte400SenzaCampi() throws Exception {
		streak(0, 0, null);
		UUID id = avvia(lezione);

		// nessuna risposta
		chiusura(mario, id).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_NON_TUTTE_RISOLTE))
				.andExpect(jsonPath("$.campi").isEmpty());

		// risposte a tutte, ma la multipla sbagliata e non ripetuta
		conOpzione(id, veroFalso, opzione(veroFalso, "Falso"));
		conOpzione(id, multipla, opzione(multipla, "A"));
		conCoppie(id, abbinamento, coppia(abbinamento, "Bicipite", "Braccio"), coppia(abbinamento, "Quadricipite", "Coscia"));
		chiusura(mario, id).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_NON_TUTTE_RISOLTE));
		assertThat(tentativo(id).getEsito()).isEqualTo(EsitoTentativo.IN_CORSO);
		assertThat(streakDiMario().getValore()).isZero();

		// ripetuta e giusta: ora si chiude
		conOpzione(id, multipla, opzione(multipla, "B"));
		chiusura(mario, id).andExpect(status().isOk())
				.andExpect(jsonPath("$.streak.valore").value(1))
				.andExpect(jsonPath("$.streak.record").value(1));
	}

	@Test
	void chiusuraIgnoraLaDomandaAggiuntaENonContaQuellaCancellata() throws Exception {
		streak(0, 0, null);
		UUID id = avvia(lezione);
		Domanda nuova = new Domanda(lezione, "Nuova", "S", TipoDomanda.VERO_FALSO, 4, Instant.now().plusSeconds(3600));
		nuova.aggiungiOpzione("Vero", true);
		nuova.aggiungiOpzione("Falso", false);
		domandaRepository.save(nuova);
		conOpzione(id, veroFalso, opzione(veroFalso, "Falso"));
		conOpzione(id, multipla, opzione(multipla, "B"));
		// l'abbinamento non è risolto, ma viene cancellato
		cancella("domanda", abbinamento.getId());

		chiusura(mario, id).andExpect(status().isOk()).andExpect(jsonPath("$.esito").value("SUPERATO"));
	}

	@Test
	void chiusuraInesistenteAltruiOLezioneCancellata404() throws Exception {
		streak(0, 0, null);
		UUID id = avvia(lezione);
		rispondiGiustoATutte(id);
		Utente luigi = utente("luigi@example.com");

		chiusura(mario, UUID.randomUUID()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_TENTATIVO_NON_TROVATO));
		chiusura(luigi, id).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_TENTATIVO_NON_TROVATO));
		cancella("lezione", lezione.getId());
		chiusura(mario, id).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_LEZIONE_NON_TROVATA));
		assertThat(tentativo(id).getEsito()).isEqualTo(EsitoTentativo.IN_CORSO);
	}

	@Test
	void chiusuraDiUnTentativoGiaConcluso409() throws Exception {
		streak(0, 0, null);
		UUID id = avvia(lezione);
		rispondiGiustoATutte(id);
		chiusura(mario, id).andExpect(status().isOk());

		chiusura(mario, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codice").value("TENTATIVO_CHIUSO"))
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_TENTATIVO_CHIUSO));
		assertThat(streakDiMario().getValore()).isEqualTo(1);
	}

	@Test
	void unTentativoFallitoNonToccaLaStreakENonSiChiude() throws Exception {
		LocalDate ieri = LocalDate.now(clock).minusDays(1);
		streak(4, 6, ieri);
		UUID id = avvia(lezione);
		conOpzione(id, veroFalso, opzione(veroFalso, "Vero"));
		conOpzione(id, multipla, opzione(multipla, "A"));
		conOpzione(id, multipla, opzione(multipla, "C")).andExpect(jsonPath("$.esito").value("FALLITO"));

		chiusura(mario, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codice").value("TENTATIVO_CHIUSO"));
		Streak streak = streakDiMario();
		assertThat(streak.getValore()).isEqualTo(4);
		assertThat(streak.getRecord()).isEqualTo(6);
		assertThat(streak.getUltimaAttivita()).isEqualTo(ieri);
	}

	// --- resoconto ---

	@Test
	void resocontoDiUnTentativoSuperatoNellOrdineDellAdminConLaPrimaRisposta() throws Exception {
		streak(0, 0, null);
		UUID id = avvia(lezione);
		// risposte in un ordine diverso da quello dell'admin; la multipla prima sbagliata e poi ripetuta giusta
		conCoppie(id, abbinamento, coppia(abbinamento, "Bicipite", "Braccio"), coppia(abbinamento, "Quadricipite", "Coscia"));
		conOpzione(id, multipla, opzione(multipla, "A"));
		conOpzione(id, veroFalso, opzione(veroFalso, "Falso"));
		conOpzione(id, multipla, opzione(multipla, "B"));
		chiusura(mario, id).andExpect(status().isOk());

		resoconto(mario, id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.esito").value("SUPERATO"))
				.andExpect(jsonPath("$.errori").value(1))
				.andExpect(jsonPath("$.iniziatoAt").isNotEmpty())
				.andExpect(jsonPath("$.finitoAt").isNotEmpty())
				.andExpect(jsonPath("$.lezione.id").value(lezione.getId().toString()))
				.andExpect(jsonPath("$.lezione.titolo").value("Lezione"))
				.andExpect(jsonPath("$.domande[*].testo", contains("Vero o falso?", "Quale?", "Abbina")))
				// vero o falso: giusta al primo colpo
				.andExpect(jsonPath("$.domande[0].tipo").value("VERO_FALSO"))
				.andExpect(jsonPath("$.domande[0].corretta").value(true))
				.andExpect(jsonPath("$.domande[0].risposta.testo").value("Falso"))
				.andExpect(jsonPath("$.domande[0].soluzione.id").value(opzione(veroFalso, "Falso").toString()))
				.andExpect(jsonPath("$.domande[0].spiegazione").value("Perché è falso"))
				.andExpect(jsonPath("$.domande[0].coppie", hasSize(0)))
				// scelta multipla: conta la prima risposta (sbagliata), non la ripetizione
				.andExpect(jsonPath("$.domande[1].corretta").value(false))
				.andExpect(jsonPath("$.domande[1].risposta.id").value(opzione(multipla, "A").toString()))
				.andExpect(jsonPath("$.domande[1].risposta.testo").value("A"))
				.andExpect(jsonPath("$.domande[1].soluzione.testo").value("B"))
				// abbinamento: coppie nell'ordine dei sinistri, risposta e soluzione null
				.andExpect(jsonPath("$.domande[2].tipo").value("ABBINAMENTO"))
				.andExpect(jsonPath("$.domande[2].corretta").value(true))
				.andExpect(jsonPath("$.domande[2].risposta").value(nullValue()))
				.andExpect(jsonPath("$.domande[2].soluzione").value(nullValue()))
				.andExpect(jsonPath("$.domande[2].coppie[*].sinistro.testo", contains("Bicipite", "Quadricipite")))
				.andExpect(jsonPath("$.domande[2].coppie[*].destro.testo", contains("Braccio", "Coscia")))
				.andExpect(jsonPath("$.domande[2].coppie[*].corretta", contains(true, true)));
	}

	@Test
	void resocontoDiUnTentativoInCorsoSoloLeDomandeRisposte() throws Exception {
		UUID id = avvia(lezione);
		conCoppie(id, abbinamento, coppia(abbinamento, "Bicipite", "Coscia"), coppia(abbinamento, "Quadricipite", "Braccio"));

		resoconto(mario, id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.esito").value("IN_CORSO"))
				.andExpect(jsonPath("$.errori").value(2))
				.andExpect(jsonPath("$.finitoAt").value(nullValue()))
				.andExpect(jsonPath("$.domande", hasSize(1)))
				.andExpect(jsonPath("$.domande[0].corretta").value(false))
				.andExpect(jsonPath("$.domande[0].coppie[*].destro.testo", contains("Coscia", "Braccio")))
				.andExpect(jsonPath("$.domande[0].coppie[*].soluzione.testo", contains("Braccio", "Coscia")))
				.andExpect(jsonPath("$.domande[0].coppie[*].corretta", contains(false, false)));
	}

	@Test
	void resocontoConDomandaELezioneCancellateDopoIlTentativo() throws Exception {
		UUID id = avvia(lezione);
		conOpzione(id, veroFalso, opzione(veroFalso, "Falso"));
		conOpzione(id, multipla, opzione(multipla, "C"));
		cancella("domanda", multipla.getId());
		cancella("lezione", lezione.getId());

		resoconto(mario, id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lezione.titolo").value("Lezione"))
				.andExpect(jsonPath("$.domande[*].testo", contains("Vero o falso?", "Quale?")))
				.andExpect(jsonPath("$.domande[1].risposta.testo").value("C"))
				.andExpect(jsonPath("$.domande[1].soluzione.testo").value("B"));
	}

	@Test
	void resocontoInesistenteOAltrui404ConLoStessoMessaggio() throws Exception {
		UUID id = avvia(lezione);
		Utente luigi = utente("luigi@example.com");

		resoconto(mario, UUID.randomUUID()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_TENTATIVO_NON_TROVATO));
		resoconto(luigi, id).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.messaggio").value(TentativiService.MSG_TENTATIVO_NON_TROVATO));
	}

	@Test
	void senzaTokenOEmailNonVerificata() throws Exception {
		mockMvc.perform(post("/api/attempts").contentType(MediaType.APPLICATION_JSON)
				.content("{\"lezioneId\": \"" + lezione.getId() + "\"}")).andExpect(status().isUnauthorized());
		Utente nonVerificato = new Utente("Nuovo", "Utente", "nuovo@example.com", "hash", CREAZIONE);
		nonVerificato = utenteRepository.save(nonVerificato);
		avvio(nonVerificato, "{\"lezioneId\": \"" + lezione.getId() + "\"}").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("EMAIL_NON_VERIFICATA"));
		mockMvc.perform(post("/api/attempts/" + UUID.randomUUID() + "/complete")).andExpect(status().isUnauthorized());
		chiusura(nonVerificato, UUID.randomUUID()).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("EMAIL_NON_VERIFICATA"));
		mockMvc.perform(get("/api/attempts/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
		resoconto(nonVerificato, UUID.randomUUID()).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("EMAIL_NON_VERIFICATA"));
	}

}
