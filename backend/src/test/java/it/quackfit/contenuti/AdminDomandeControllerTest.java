package it.quackfit.contenuti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import it.quackfit.security.JwtService;
import it.quackfit.utente.Ruolo;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/**
 * Test di integrazione delle domande con H2 (admin.md, Domande). Le singole regole del tipo sono in RegoleDomandaTest:
 * qui si provano il flusso, il formato delle risposte e che gli errori arrivino come 400 con i campi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDomandeControllerTest {

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
	private JdbcTemplate jdbc;

	private String tokenAdmin;
	private Lezione lezione;

	@BeforeEach
	void setUp() {
		pulisci();
		Utente admin = new Utente("Anna", "Admin", "anna@example.com", "hash", CREAZIONE);
		admin.setRuolo(Ruolo.ADMIN);
		admin.setEmailVerificata(true);
		tokenAdmin = "Bearer " + jwtService.genera(utenteRepository.save(admin));
		Tappa tappa = tappaRepository.save(new Tappa("Tappa", "Descrizione", 1, CREAZIONE));
		lezione = lezioneRepository.save(new Lezione(tappa, "Lezione", "Descrizione", 1, CREAZIONE));
	}

	/** Tappa, lezione e domanda hanno il soft delete: deleteAll() non toglierebbe le righe. */
	@AfterEach
	void pulisci() {
		jdbc.update("update elemento_abbinamento set abbinamento_corretto_id = null");
		jdbc.update("delete from elemento_abbinamento");
		jdbc.update("delete from opzione");
		jdbc.update("delete from domanda");
		jdbc.update("delete from lezione");
		jdbc.update("delete from tappa");
		utenteRepository.deleteAll();
	}

	// --- richieste ---

	private ResultActions conBody(MockHttpServletRequestBuilder richiesta, String json) throws Exception {
		return mockMvc.perform(richiesta.header(HttpHeaders.AUTHORIZATION, tokenAdmin)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions crea(String json) throws Exception {
		return conBody(post("/api/admin/lessons/" + lezione.getId() + "/questions"), json);
	}

	private ResultActions modifica(String id, String json) throws Exception {
		return conBody(patch("/api/admin/questions/" + id), json);
	}

	private ResultActions cancella(String id) throws Exception {
		return mockMvc.perform(delete("/api/admin/questions/" + id).header(HttpHeaders.AUTHORIZATION, tokenAdmin));
	}

	/** La lezione riletta dal database con il dettaglio admin. */
	private String dettaglio() throws Exception {
		return mockMvc.perform(get("/api/admin/lessons/" + lezione.getId()).header(HttpHeaders.AUTHORIZATION, tokenAdmin))
				.andReturn().getResponse().getContentAsString();
	}

	private static final String VERO_FALSO = """
			{"testo": "Il riscaldamento serve?", "spiegazione": "Sì", "tipo": "VERO_FALSO", "rispostaVero": false}""";

	private static final String MULTIPLA = """
			{"testo": "Quale?", "spiegazione": "Perché", "tipo": "SCELTA_MULTIPLA",
			 "opzioni": [{"testo": "Panca", "corretta": false}, {"testo": " Squat ", "corretta": true},
			             {"testo": "Stacco"}]}""";

	private static final String ABBINAMENTO = """
			{"testo": "Abbina", "spiegazione": "Così", "tipo": "ABBINAMENTO",
			 "coppie": [{"sinistra": "Bicipite", "destra": "Braccio"}, {"sinistra": "Quadricipite", "destra": "Coscia"}]}""";

	private String idCreata(String json) throws Exception {
		return JsonPath.read(crea(json).andReturn().getResponse().getContentAsString(), "$.id");
	}

	// --- creazione ---

	@Test
	void creazioneDeiTreTipiInFondoConLeSoluzioni() throws Exception {
		crea(VERO_FALSO)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.tipo").value("VERO_FALSO"))
				.andExpect(jsonPath("$.ordine").value(1))
				.andExpect(jsonPath("$.spiegazione").value("Sì"))
				.andExpect(jsonPath("$.opzioni[*].testo", contains("Vero", "Falso")))
				.andExpect(jsonPath("$.opzioni[*].corretta", contains(false, true)))
				.andExpect(jsonPath("$.coppie").isArray())
				.andExpect(jsonPath("$.coppie").isEmpty());
		crea(MULTIPLA)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ordine").value(2))
				.andExpect(jsonPath("$.opzioni[*].testo", contains("Panca", "Squat", "Stacco")))
				.andExpect(jsonPath("$.opzioni[*].corretta", contains(false, true, false)))
				.andExpect(jsonPath("$.opzioni[0].id").exists())
				.andExpect(jsonPath("$.coppie").isEmpty());
		crea(ABBINAMENTO)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ordine").value(3))
				.andExpect(jsonPath("$.opzioni").isArray())
				.andExpect(jsonPath("$.opzioni").isEmpty())
				.andExpect(jsonPath("$.coppie[*].sinistro.testo", contains("Bicipite", "Quadricipite")))
				.andExpect(jsonPath("$.coppie[*].destro.testo", contains("Braccio", "Coscia")))
				.andExpect(jsonPath("$.coppie[0].destro.id").exists());

		// riletta dal database: stessi dati e collegamenti dell'abbinamento salvati
		String json = dettaglio();
		assertThat(JsonPath.<List<Integer>>read(json, "$.domande[*].ordine")).containsExactly(1, 2, 3);
		assertThat(JsonPath.<List<String>>read(json, "$.domande[2].coppie[*].destro.testo"))
				.containsExactly("Braccio", "Coscia");
	}

	@Test
	void testiMancantiETroppoLunghi400ConICampi() throws Exception {
		crea("""
				{"testo": " ", "tipo": "SCELTA_MULTIPLA",
				 "opzioni": [{"testo": ""}, {"testo": "%s", "corretta": true}]}""".formatted("x".repeat(201)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.testo").value("Il testo è obbligatorio"))
				.andExpect(jsonPath("$.campi.spiegazione").value("La spiegazione è obbligatoria"))
				.andExpect(jsonPath("$.campi['opzioni[0].testo']").value("Il testo è obbligatorio"))
				.andExpect(jsonPath("$.campi['opzioni[1].testo']").value("Massimo 200 caratteri"));
		crea("""
				{"testo": "%s", "spiegazione": "%s", "tipo": "VERO_FALSO", "rispostaVero": true}"""
				.formatted("x".repeat(501), "y".repeat(1001)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.testo").value("Massimo 500 caratteri"))
				.andExpect(jsonPath("$.campi.spiegazione").value("Massimo 1000 caratteri"));
		crea("""
				{"testo": "T", "spiegazione": "S"}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.tipo").value("Il tipo è obbligatorio"));
	}

	@Test
	void regoleDelTipoViolate400ConICampiENienteSalvato() throws Exception {
		crea("""
				{"testo": "T", "spiegazione": "S", "tipo": "SCELTA_MULTIPLA", "rispostaVero": true,
				 "opzioni": [{"testo": "A", "corretta": true}, {"testo": "B", "corretta": true}]}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.rispostaVero").value(RegoleDomanda.MSG_NON_PREVISTO))
				.andExpect(jsonPath("$.campi.opzioni").value(RegoleDomanda.MSG_UNA_CORRETTA));
		crea("""
				{"testo": "T", "spiegazione": "S", "tipo": "ABBINAMENTO",
				 "coppie": [{"sinistra": "A", "destra": "Braccio"}, {"sinistra": "B", "destra": " braccio "}]}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi['coppie[1].destra']").value(RegoleDomanda.MSG_DESTRA_UGUALE));
		crea("""
				{"testo": "T", "spiegazione": "S", "tipo": "VERO_FALSO"}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.rispostaVero").value(RegoleDomanda.MSG_RISPOSTA_VERO));

		assertThat(jdbc.queryForObject("select count(*) from domanda", Integer.class)).isZero();
	}

	@Test
	void creazioneInLezioneInesistenteOCancellata404() throws Exception {
		conBody(post("/api/admin/lessons/" + UUID.randomUUID() + "/questions"), VERO_FALSO)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codice").value("NON_TROVATO"));
		jdbc.update("update lezione set deleted_at = ? where id = ?", Timestamp.from(CREAZIONE), lezione.getId());
		crea(VERO_FALSO).andExpect(status().isNotFound());

		assertThat(jdbc.queryForObject("select count(*) from domanda", Integer.class)).isZero();
	}

	// --- modifica ---

	@Test
	void modificaDeiSoliTestiConLaStrutturaInvariata() throws Exception {
		String multipla = idCreata(MULTIPLA);
		String json = dettaglio();
		String panca = JsonPath.read(json, "$.domande[0].opzioni[0].id");

		// Campi di struttura presenti (tipo, corretta, rispostaVero, coppie): ignorati
		modifica(multipla, """
				{"testo": " Nuovo testo ", "tipo": "VERO_FALSO", "rispostaVero": true,
				 "coppie": [{"sinistra": "X", "destra": "Y"}],
				 "opzioni": [{"id": "%s", "testo": "Panca piana", "corretta": true}]}""".formatted(panca))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.testo").value("Nuovo testo"))
				.andExpect(jsonPath("$.spiegazione").value("Perché"))
				.andExpect(jsonPath("$.tipo").value("SCELTA_MULTIPLA"))
				.andExpect(jsonPath("$.opzioni[*].testo", contains("Panca piana", "Squat", "Stacco")));

		json = dettaglio();
		assertThat(JsonPath.<String>read(json, "$.domande[0].tipo")).isEqualTo("SCELTA_MULTIPLA");
		assertThat(JsonPath.<String>read(json, "$.domande[0].testo")).isEqualTo("Nuovo testo");
		assertThat(JsonPath.<List<String>>read(json, "$.domande[0].opzioni[*].testo"))
				.containsExactly("Panca piana", "Squat", "Stacco");
		assertThat(JsonPath.<List<Boolean>>read(json, "$.domande[0].opzioni[*].corretta"))
				.containsExactly(false, true, false);
		assertThat(JsonPath.<List<Object>>read(json, "$.domande[0].coppie")).isEmpty();
	}

	@Test
	void modificaDegliElementiDiUnAbbinamento() throws Exception {
		String abbinamento = idCreata(ABBINAMENTO);
		String json = dettaglio();
		String braccio = JsonPath.read(json, "$.domande[0].coppie[0].destro.id");

		modifica(abbinamento, """
				{"elementi": [{"id": "%s", "testo": "Parte alta del braccio"}]}""".formatted(braccio))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.coppie[0].sinistro.testo").value("Bicipite"))
				.andExpect(jsonPath("$.coppie[0].destro.testo").value("Parte alta del braccio"))
				.andExpect(jsonPath("$.coppie[1].destro.testo").value("Coscia"));
		assertThat(JsonPath.<String>read(dettaglio(), "$.domande[0].coppie[0].destro.id")).isEqualTo(braccio);
	}

	@Test
	void modificaCheRendeUnTestoRipetuto400SulCampo() throws Exception {
		String multipla = idCreata(MULTIPLA);
		String stacco = JsonPath.read(dettaglio(), "$.domande[0].opzioni[2].id");

		modifica(multipla, """
				{"testo": "Cambiato", "opzioni": [{"id": "%s", "testo": "SQUAT"}]}""".formatted(stacco))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi['opzioni[0].testo']").value(RegoleDomanda.MSG_OPZIONE_UGUALE));

		// nulla è stato salvato, nemmeno il testo della domanda
		String json = dettaglio();
		assertThat(JsonPath.<String>read(json, "$.domande[0].testo")).isEqualTo("Quale?");
		assertThat(JsonPath.<String>read(json, "$.domande[0].opzioni[2].testo")).isEqualTo("Stacco");
	}

	@Test
	void modificaNonAmmessa400() throws Exception {
		String veroFalso = idCreata(VERO_FALSO);
		String multipla = idCreata(MULTIPLA);
		String json = dettaglio();
		String vero = JsonPath.read(json, "$.domande[0].opzioni[0].id");

		modifica(veroFalso, """
				{"opzioni": [{"id": "%s", "testo": "Giusto"}]}""".formatted(vero))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.opzioni").value(RegoleDomanda.MSG_VERO_FALSO_FISSI));
		// id di un'altra domanda
		modifica(multipla, """
				{"opzioni": [{"id": "%s", "testo": "X"}]}""".formatted(vero))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi['opzioni[0].id']").value(RegoleDomanda.MSG_ID_ESTRANEO));
		modifica(multipla, """
				{"testo": "", "opzioni": [{"testo": "X"}]}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.testo").value("Il testo non può essere vuoto"))
				.andExpect(jsonPath("$.campi['opzioni[0].id']").value("L'id è obbligatorio"));
		modifica(UUID.randomUUID().toString(), "{\"testo\": \"X\"}").andExpect(status().isNotFound());

		assertThat(JsonPath.<String>read(dettaglio(), "$.domande[0].opzioni[0].testo")).isEqualTo("Vero");
	}

	// --- cancellazione ---

	@Test
	void cancellazioneConRinumerazioneFinoAllUltimaDomanda() throws Exception {
		String prima = idCreata(VERO_FALSO);
		String seconda = idCreata(MULTIPLA);
		String terza = idCreata(ABBINAMENTO);

		cancella(seconda).andExpect(status().isNoContent());

		String json = dettaglio();
		assertThat(JsonPath.<List<String>>read(json, "$.domande[*].id")).containsExactly(prima, terza);
		assertThat(JsonPath.<List<Integer>>read(json, "$.domande[*].ordine")).containsExactly(1, 2);
		assertThat(jdbc.queryForObject("select deleted_at from domanda where id = ?", Timestamp.class,
				UUID.fromString(seconda))).isNotNull();
		// opzioni ed elementi restano (il resoconto dei tentativi li mostra ancora)
		assertThat(jdbc.queryForObject("select count(*) from opzione", Integer.class)).isEqualTo(5);

		// una nuova domanda va in fondo, dopo la rinumerazione
		crea(VERO_FALSO).andExpect(jsonPath("$.ordine").value(3));

		cancella(prima).andExpect(status().isNoContent());
		cancella(terza).andExpect(status().isNoContent());
		json = dettaglio();
		assertThat(JsonPath.<List<Integer>>read(json, "$.domande[*].ordine")).containsExactly(1);
		cancella(JsonPath.read(json, "$.domande[0].id")).andExpect(status().isNoContent());

		// l'ultima domanda si può cancellare: la lezione resta, senza domande
		mockMvc.perform(get("/api/admin/lessons/" + lezione.getId()).header(HttpHeaders.AUTHORIZATION, tokenAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.domande", hasSize(0)));
		cancella(prima).andExpect(status().isNotFound());
	}

	// --- accessi ---

	@Test
	void unUtenteNormale403() throws Exception {
		Utente mario = new Utente("Mario", "Rossi", "mario@example.com", "hash", CREAZIONE);
		mario.setEmailVerificata(true);
		String tokenMario = "Bearer " + jwtService.genera(utenteRepository.save(mario));
		String domanda = idCreata(VERO_FALSO);

		mockMvc.perform(post("/api/admin/lessons/" + lezione.getId() + "/questions")
						.header(HttpHeaders.AUTHORIZATION, tokenMario)
						.contentType(MediaType.APPLICATION_JSON).content(VERO_FALSO))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("ACCESSO_NEGATO"));
		mockMvc.perform(delete("/api/admin/questions/" + domanda).header(HttpHeaders.AUTHORIZATION, tokenMario))
				.andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("select count(*) from domanda where deleted_at is null", Integer.class))
				.isEqualTo(1);
	}

}
