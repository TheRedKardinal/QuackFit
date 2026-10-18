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

import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import it.quackfit.security.JwtService;
import it.quackfit.utente.Ruolo;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/**
 * Test di integrazione della gestione di tappe e lezioni con H2 (admin.md, Ordine, Tappe, Lezioni).
 * Il lock pessimistico non si prova qui: su H2 non dimostra la concorrenza (verifica manuale su PostgreSQL).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminContenutiControllerTest {

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
	private JdbcTemplate jdbc;

	private String tokenAdmin;

	@BeforeEach
	void setUp() {
		pulisci();
		Utente admin = new Utente("Anna", "Admin", "anna@example.com", "hash", CREAZIONE);
		admin.setRuolo(Ruolo.ADMIN);
		admin.setEmailVerificata(true);
		tokenAdmin = "Bearer " + jwtService.genera(utenteRepository.save(admin));
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

	// --- dati di prova ---

	private Tappa tappa(String titolo, int ordine) {
		return tappaRepository.save(new Tappa(titolo, "Descrizione " + titolo, ordine, CREAZIONE));
	}

	private Lezione lezione(Tappa tappa, String titolo, int ordine) {
		return lezioneRepository.save(new Lezione(tappa, titolo, "Descrizione " + titolo, ordine, CREAZIONE));
	}

	private Domanda veroFalso(Lezione lezione, int ordine) {
		Domanda domanda = new Domanda(lezione, "Domanda " + ordine, "Spiegazione", TipoDomanda.VERO_FALSO, ordine,
				CREAZIONE);
		domanda.aggiungiOpzione("Vero", true);
		domanda.aggiungiOpzione("Falso", false);
		return domandaRepository.save(domanda);
	}

	/** Soft delete di prova: i metodi cancella… dei repository chiedono una transazione. */
	private void cancella(String tabella, UUID id, Instant istante) {
		jdbc.update("update " + tabella + " set deleted_at = ? where id = ?", java.sql.Timestamp.from(istante), id);
	}

	private Instant deletedAt(String tabella, UUID id) {
		java.sql.Timestamp valore = jdbc.queryForObject("select deleted_at from " + tabella + " where id = ?",
				java.sql.Timestamp.class, id);
		return valore == null ? null : valore.toInstant();
	}

	// --- richieste ---

	private ResultActions esegui(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder richiesta)
			throws Exception {
		return mockMvc.perform(richiesta.header(HttpHeaders.AUTHORIZATION, tokenAdmin));
	}

	private ResultActions conBody(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder richiesta,
			String json) throws Exception {
		return esegui(richiesta.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions elenco() throws Exception {
		return esegui(get("/api/admin/stages"));
	}

	/** Titoli e ordini delle tappe rilette dall'elenco: devono essere 1…n senza buchi né duplicati. */
	private List<String> titoliTappe() throws Exception {
		String json = elenco().andReturn().getResponse().getContentAsString();
		List<Integer> ordini = JsonPath.read(json, "$[*].ordine");
		assertThat(ordini).isEqualTo(java.util.stream.IntStream.rangeClosed(1, ordini.size()).boxed().toList());
		return JsonPath.read(json, "$[*].titolo");
	}

	private List<String> titoliLezioni(Tappa tappa) throws Exception {
		String json = elenco().andReturn().getResponse().getContentAsString();
		List<Integer> ordini = JsonPath.read(json, "$[?(@.id == '" + tappa.getId() + "')].lezioni[*].ordine");
		assertThat(ordini).isEqualTo(java.util.stream.IntStream.rangeClosed(1, ordini.size()).boxed().toList());
		return JsonPath.read(json, "$[?(@.id == '" + tappa.getId() + "')].lezioni[*].titolo");
	}

	// --- elenco ---

	@Test
	void elencoInOrdineConLezioniENumeroDomande() throws Exception {
		Tappa seconda = tappa("Seconda", 2);
		Tappa prima = tappa("Prima", 1);
		Lezione l2 = lezione(prima, "L2", 2);
		Lezione l1 = lezione(prima, "L1", 1);
		veroFalso(l1, 1);
		veroFalso(l1, 2);
		Domanda cancellata = veroFalso(l1, 3);
		cancella("domanda", cancellata.getId(), CREAZIONE);
		Lezione eliminata = lezione(seconda, "Eliminata", 1);
		cancella("lezione", eliminata.getId(), CREAZIONE);

		elenco()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].titolo", contains("Prima", "Seconda")))
				.andExpect(jsonPath("$[0].descrizione").value("Descrizione Prima"))
				.andExpect(jsonPath("$[0].lezioni[*].titolo", contains("L1", "L2")))
				.andExpect(jsonPath("$[0].lezioni[0].id").value(l1.getId().toString()))
				.andExpect(jsonPath("$[0].lezioni[0].tappaId").value(prima.getId().toString()))
				.andExpect(jsonPath("$[0].lezioni[0].numeroDomande").value(2))
				.andExpect(jsonPath("$[0].lezioni[1].id").value(l2.getId().toString()))
				.andExpect(jsonPath("$[0].lezioni[1].numeroDomande").value(0))
				.andExpect(jsonPath("$[1].lezioni", hasSize(0)));
	}

	// --- creazione ---

	@Test
	void creazioneTappaInFondoConLezioniVuote() throws Exception {
		tappa("Prima", 1);
		tappa("Seconda", 2);

		conBody(post("/api/admin/stages"), "{\"titolo\": \"  Terza  \", \"descrizione\": \" Nuova \"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.titolo").value("Terza"))
				.andExpect(jsonPath("$.descrizione").value("Nuova"))
				.andExpect(jsonPath("$.ordine").value(3))
				.andExpect(jsonPath("$.lezioni", hasSize(0)));
		assertThat(titoliTappe()).containsExactly("Prima", "Seconda", "Terza");
	}

	@Test
	void primaTappaAdUnaTabellaVuotaHaOrdine1() throws Exception {
		conBody(post("/api/admin/stages"), "{\"titolo\": \"Unica\", \"descrizione\": \"D\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ordine").value(1));
	}

	@Test
	void creazioneLezioneInFondoAllaTappaConZeroDomande() throws Exception {
		Tappa prima = tappa("Prima", 1);
		Tappa altra = tappa("Altra", 2);
		lezione(prima, "L1", 1);
		lezione(altra, "X1", 1);
		lezione(altra, "X2", 2);

		conBody(post("/api/admin/stages/" + prima.getId() + "/lessons"), "{\"titolo\": \"L2\", \"descrizione\": \"D\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.titolo").value("L2"))
				.andExpect(jsonPath("$.descrizione").value("D"))
				.andExpect(jsonPath("$.ordine").value(2))
				.andExpect(jsonPath("$.tappaId").value(prima.getId().toString()))
				.andExpect(jsonPath("$.numeroDomande").value(0));
		assertThat(titoliLezioni(prima)).containsExactly("L1", "L2");
	}

	@Test
	void creazioneLezioneInTappaInesistenteOCancellata404() throws Exception {
		Tappa cancellata = tappa("Cancellata", 1);
		cancella("tappa", cancellata.getId(), CREAZIONE);

		for (UUID id : new UUID[] { UUID.randomUUID(), cancellata.getId() }) {
			conBody(post("/api/admin/stages/" + id + "/lessons"), "{\"titolo\": \"L\", \"descrizione\": \"D\"}")
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.codice").value("NON_TROVATO"));
		}
	}

	@Test
	void creazioneConCampiMancantiOTroppoLunghi400() throws Exception {
		conBody(post("/api/admin/stages"), "{\"titolo\": \"   \"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.titolo").value("Il titolo è obbligatorio"))
				.andExpect(jsonPath("$.campi.descrizione").value("La descrizione è obbligatoria"));
		conBody(post("/api/admin/stages"), Map.of("titolo", "x".repeat(101), "descrizione", "y".repeat(301)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.titolo").value("Massimo 100 caratteri"))
				.andExpect(jsonPath("$.campi.descrizione").value("Massimo 300 caratteri"));
		// i limiti si contano dopo strip(): 100 caratteri più spazi vanno bene
		conBody(post("/api/admin/stages"), Map.of("titolo", " " + "x".repeat(100) + " ", "descrizione", "y".repeat(300)))
				.andExpect(status().isCreated());

		Tappa tappa = tappa("T", 2);
		conBody(post("/api/admin/stages/" + tappa.getId() + "/lessons"), Map.of("titolo", "x".repeat(101), "descrizione", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.titolo").value("Massimo 100 caratteri"))
				.andExpect(jsonPath("$.campi.descrizione").value("La descrizione è obbligatoria"));
	}

	private ResultActions conBody(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder richiesta,
			Map<String, String> campi) throws Exception {
		return conBody(richiesta, new tools.jackson.databind.ObjectMapper().writeValueAsString(campi));
	}

	// --- modifica e riordino ---

	@Test
	void modificaParzialeLasciaInvariatiICampiAssenti() throws Exception {
		Tappa tappa = tappa("Vecchio", 1);
		Lezione lezione = lezione(tappa, "L1", 1);
		veroFalso(lezione, 1);

		conBody(patch("/api/admin/stages/" + tappa.getId()), "{\"titolo\": \" Nuovo \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.titolo").value("Nuovo"))
				.andExpect(jsonPath("$.descrizione").value("Descrizione Vecchio"))
				.andExpect(jsonPath("$.ordine").value(1))
				.andExpect(jsonPath("$.lezioni[*].titolo", contains("L1")))
				.andExpect(jsonPath("$.lezioni[0].numeroDomande").value(1));
		conBody(patch("/api/admin/lessons/" + lezione.getId()), "{\"descrizione\": \"Nuova\", \"titolo\": null}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.titolo").value("L1"))
				.andExpect(jsonPath("$.descrizione").value("Nuova"))
				.andExpect(jsonPath("$.tappaId").value(tappa.getId().toString()))
				.andExpect(jsonPath("$.numeroDomande").value(1));

		assertThat(tappaRepository.findById(tappa.getId()).orElseThrow().getTitolo()).isEqualTo("Nuovo");
		assertThat(lezioneRepository.findById(lezione.getId()).orElseThrow().getDescrizione()).isEqualTo("Nuova");
	}

	@Test
	void modificaConTestiVuotiOTroppoLunghi400() throws Exception {
		Tappa tappa = tappa("T", 1);
		Lezione lezione = lezione(tappa, "L", 1);

		conBody(patch("/api/admin/stages/" + tappa.getId()), "{\"titolo\": \"  \", \"descrizione\": \"\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.titolo").value("Il titolo non può essere vuoto"))
				.andExpect(jsonPath("$.campi.descrizione").value("La descrizione non può essere vuota"));
		conBody(patch("/api/admin/lessons/" + lezione.getId()), Map.of("titolo", "x".repeat(101)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.titolo").value("Massimo 100 caratteri"));
		assertThat(tappaRepository.findById(tappa.getId()).orElseThrow().getTitolo()).isEqualTo("T");
	}

	@Test
	void riordinoTappeVersoLAltoEVersoIlBasso() throws Exception {
		UUID a = tappa("A", 1).getId();
		tappa("B", 2);
		tappa("C", 3);
		UUID d = tappa("D", 4).getId();

		conBody(patch("/api/admin/stages/" + d), "{\"ordine\": 2}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ordine").value(2));
		assertThat(titoliTappe()).containsExactly("A", "D", "B", "C");

		conBody(patch("/api/admin/stages/" + a), "{\"ordine\": 4}")
				.andExpect(jsonPath("$.ordine").value(4));
		assertThat(titoliTappe()).containsExactly("D", "B", "C", "A");
	}

	@Test
	void riordinoLezioniSoloDentroLaTappa() throws Exception {
		Tappa prima = tappa("Prima", 1);
		Tappa altra = tappa("Altra", 2);
		lezione(prima, "L1", 1);
		lezione(prima, "L2", 2);
		Lezione l3 = lezione(prima, "L3", 3);
		lezione(altra, "X1", 1);
		lezione(altra, "X2", 2);

		conBody(patch("/api/admin/lessons/" + l3.getId()), "{\"ordine\": 1, \"titolo\": \"L3 bis\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ordine").value(1))
				.andExpect(jsonPath("$.titolo").value("L3 bis"));

		assertThat(titoliLezioni(prima)).containsExactly("L3 bis", "L1", "L2");
		assertThat(titoliLezioni(altra)).containsExactly("X1", "X2");
	}

	@Test
	void posizioneFuoriLimite400ENessunaModifica() throws Exception {
		Tappa a = tappa("A", 1);
		tappa("B", 2);
		Lezione lezione = lezione(a, "L1", 1);

		conBody(patch("/api/admin/stages/" + a.getId()), "{\"ordine\": 3, \"titolo\": \"Cambiato\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.ordine").value("La posizione deve essere tra 1 e 2"));
		conBody(patch("/api/admin/stages/" + a.getId()), "{\"ordine\": 0}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.ordine").value("La posizione deve essere almeno 1"));
		conBody(patch("/api/admin/lessons/" + lezione.getId()), "{\"ordine\": 2}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.ordine").value("La posizione deve essere tra 1 e 1"));

		assertThat(titoliTappe()).containsExactly("A", "B");
	}

	@Test
	void modificaDiElementiInesistentiOCancellati404() throws Exception {
		Tappa cancellata = tappa("C", 1);
		Lezione lezione = lezione(cancellata, "L", 1);
		cancella("lezione", lezione.getId(), CREAZIONE);
		cancella("tappa", cancellata.getId(), CREAZIONE);

		conBody(patch("/api/admin/stages/" + cancellata.getId()), "{\"titolo\": \"X\"}").andExpect(status().isNotFound());
		conBody(patch("/api/admin/stages/" + cancellata.getId()), "{\"ordine\": 1}").andExpect(status().isNotFound());
		conBody(patch("/api/admin/lessons/" + lezione.getId()), "{\"titolo\": \"X\"}").andExpect(status().isNotFound());
		conBody(patch("/api/admin/lessons/" + lezione.getId()), "{\"ordine\": 1}").andExpect(status().isNotFound());
		esegui(get("/api/admin/lessons/" + lezione.getId())).andExpect(status().isNotFound());
		esegui(delete("/api/admin/lessons/" + UUID.randomUUID())).andExpect(status().isNotFound());
		esegui(delete("/api/admin/stages/" + cancellata.getId())).andExpect(status().isNotFound());
	}

	// --- cancellazione ---

	@Test
	void cancellazioneTappaACascataConLoStessoIstanteERinumerazione() throws Exception {
		Tappa a = tappa("A", 1);
		Tappa b = tappa("B", 2);
		tappa("C", 3);
		Lezione l1 = lezione(b, "L1", 1);
		Lezione l2 = lezione(b, "L2", 2);
		Domanda d1 = veroFalso(l1, 1);
		Domanda d2 = veroFalso(l2, 1);
		Domanda giaCancellata = veroFalso(l2, 2);
		Instant prima = Instant.parse("2026-02-01T10:00:00Z");
		cancella("domanda", giaCancellata.getId(), prima);
		Lezione altra = lezione(a, "Altra", 1);

		esegui(delete("/api/admin/stages/" + b.getId())).andExpect(status().isNoContent());

		Instant istante = deletedAt("tappa", b.getId());
		assertThat(istante).isNotNull();
		assertThat(deletedAt("lezione", l1.getId())).isEqualTo(istante);
		assertThat(deletedAt("lezione", l2.getId())).isEqualTo(istante);
		assertThat(deletedAt("domanda", d1.getId())).isEqualTo(istante);
		assertThat(deletedAt("domanda", d2.getId())).isEqualTo(istante);
		assertThat(deletedAt("domanda", giaCancellata.getId())).isEqualTo(prima);
		assertThat(deletedAt("lezione", altra.getId())).isNull();
		// opzioni ed elementi restano (il resoconto dei tentativi li mostra ancora)
		assertThat(jdbc.queryForObject("select count(*) from opzione", Integer.class)).isEqualTo(6);
		assertThat(titoliTappe()).containsExactly("A", "C");
	}

	@Test
	void cancellazioneLezioneACascataERinumerazioneNellaTappa() throws Exception {
		Tappa tappa = tappa("T", 1);
		Tappa altra = tappa("Altra", 2);
		lezione(tappa, "L1", 1);
		Lezione l2 = lezione(tappa, "L2", 2);
		lezione(tappa, "L3", 3);
		lezione(altra, "X1", 1);
		Domanda domanda = veroFalso(l2, 1);

		esegui(delete("/api/admin/lessons/" + l2.getId())).andExpect(status().isNoContent());

		Instant istante = deletedAt("lezione", l2.getId());
		assertThat(istante).isNotNull();
		assertThat(deletedAt("domanda", domanda.getId())).isEqualTo(istante);
		assertThat(titoliLezioni(tappa)).containsExactly("L1", "L3");
		assertThat(titoliLezioni(altra)).containsExactly("X1");
		assertThat(titoliTappe()).containsExactly("T", "Altra");
		esegui(delete("/api/admin/lessons/" + l2.getId())).andExpect(status().isNotFound());
	}

	// --- dettaglio ---

	@Test
	void dettaglioLezioneConLeDomandeELeSoluzioni() throws Exception {
		Tappa tappa = tappa("T", 1);
		Lezione lezione = lezione(tappa, "L", 1);
		Domanda vf = veroFalso(lezione, 1);
		Domanda multipla = new Domanda(lezione, "Quale?", "Perché", TipoDomanda.SCELTA_MULTIPLA, 2, CREAZIONE);
		multipla.aggiungiOpzione("A", false);
		multipla.aggiungiOpzione("B", true);
		multipla.aggiungiOpzione("C", false);
		domandaRepository.save(multipla);
		Domanda abbinamento = new Domanda(lezione, "Abbina", "Così", TipoDomanda.ABBINAMENTO, 3, CREAZIONE);
		abbinamento.aggiungiCoppia("Bicipite", "Braccio");
		abbinamento.aggiungiCoppia("Quadricipite", "Coscia");
		domandaRepository.save(abbinamento);
		Domanda cancellata = veroFalso(lezione, 4);
		cancella("domanda", cancellata.getId(), CREAZIONE);

		esegui(get("/api/admin/lessons/" + lezione.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(lezione.getId().toString()))
				.andExpect(jsonPath("$.titolo").value("L"))
				.andExpect(jsonPath("$.descrizione").value("Descrizione L"))
				.andExpect(jsonPath("$.ordine").value(1))
				.andExpect(jsonPath("$.tappaId").value(tappa.getId().toString()))
				.andExpect(jsonPath("$.domande", hasSize(3)))
				.andExpect(jsonPath("$.domande[0].id").value(vf.getId().toString()))
				.andExpect(jsonPath("$.domande[0].tipo").value("VERO_FALSO"))
				.andExpect(jsonPath("$.domande[0].spiegazione").value("Spiegazione"))
				.andExpect(jsonPath("$.domande[0].ordine").value(1))
				.andExpect(jsonPath("$.domande[0].opzioni[*].testo", contains("Vero", "Falso")))
				.andExpect(jsonPath("$.domande[0].opzioni[*].corretta", contains(true, false)))
				.andExpect(jsonPath("$.domande[0].coppie").isArray())
				.andExpect(jsonPath("$.domande[0].coppie").isEmpty())
				.andExpect(jsonPath("$.domande[1].opzioni[*].testo", contains("A", "B", "C")))
				.andExpect(jsonPath("$.domande[1].opzioni[*].corretta", contains(false, true, false)))
				.andExpect(jsonPath("$.domande[1].opzioni[0].id").exists())
				.andExpect(jsonPath("$.domande[2].tipo").value("ABBINAMENTO"))
				.andExpect(jsonPath("$.domande[1].coppie").isArray())
				.andExpect(jsonPath("$.domande[1].coppie").isEmpty())
				.andExpect(jsonPath("$.domande[2].opzioni").isArray())
				.andExpect(jsonPath("$.domande[2].opzioni").isEmpty())
				.andExpect(jsonPath("$.domande[2].coppie[*].sinistro.testo", contains("Bicipite", "Quadricipite")))
				.andExpect(jsonPath("$.domande[2].coppie[*].destro.testo", contains("Braccio", "Coscia")))
				.andExpect(jsonPath("$.domande[2].coppie[0].destro.id").exists());
	}

	// --- accessi ---

	@Test
	void unUtenteNormale403ESenzaToken401() throws Exception {
		Utente mario = new Utente("Mario", "Rossi", "mario@example.com", "hash", CREAZIONE);
		mario.setEmailVerificata(true);
		String tokenMario = "Bearer " + jwtService.genera(utenteRepository.save(mario));
		Tappa tappa = tappa("T", 1);

		mockMvc.perform(get("/api/admin/stages").header(HttpHeaders.AUTHORIZATION, tokenMario))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("ACCESSO_NEGATO"));
		mockMvc.perform(delete("/api/admin/stages/" + tappa.getId()).header(HttpHeaders.AUTHORIZATION, tokenMario))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/stages")).andExpect(status().isUnauthorized());
		assertThat(tappaRepository.findById(tappa.getId())).isPresent();
	}

}
