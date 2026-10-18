package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Test di integrazione della gestione utenti con H2. I token sono emessi 10 secondi fa, così il logout ovunque
 * del cambio ruolo li rende davvero non validi (iat confrontato al secondo, vedi auth.md, Note).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUtentiControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private StreakRepository streakRepository;

	@Value("${quackfit.jwt.segreto}")
	private String segreto;

	private Utente admin;
	private Utente superadmin;

	@BeforeEach
	void setUp() {
		pulisci();
		admin = salva("Anna", "Admin", "anna@example.com", Ruolo.ADMIN, Instant.parse("2026-01-01T10:00:00Z"));
		superadmin = salva("Super", "Admin", "super@example.com", Ruolo.SUPERADMIN, Instant.parse("2026-01-01T09:00:00Z"));
	}

	@AfterEach
	void pulisci() {
		streakRepository.deleteAll();
		utenteRepository.deleteAll();
	}

	private Utente salva(String nome, String cognome, String email, Ruolo ruolo, Instant creazione) {
		Utente utente = new Utente(nome, cognome, email, "hash", creazione);
		utente.setRuolo(ruolo);
		utente.setEmailVerificata(true);
		return utenteRepository.save(utente);
	}

	private String token(Utente utente) {
		Instant adesso = Instant.now();
		return "Bearer " + Jwts.builder()
				.subject(utente.getId().toString())
				.issuedAt(Date.from(adesso.minusSeconds(10)))
				.expiration(Date.from(adesso.plusSeconds(3600)))
				.signWith(Keys.hmacShaKeyFor(segreto.getBytes(StandardCharsets.UTF_8)))
				.compact();
	}

	private ResultActions lista(Utente chi, String query) throws Exception {
		return mockMvc.perform(get("/api/admin/users" + query).header(HttpHeaders.AUTHORIZATION, token(chi)));
	}

	private ResultActions cerca(Utente chi, String q) throws Exception {
		return mockMvc.perform(get("/api/admin/users").param("q", q).header(HttpHeaders.AUTHORIZATION, token(chi)));
	}

	private ResultActions cambiaRuolo(Utente chi, Utente bersaglio, String ruolo) throws Exception {
		return mockMvc.perform(patch("/api/admin/users/" + bersaglio.getId() + "/role")
				.header(HttpHeaders.AUTHORIZATION, token(chi))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ruolo\": \"" + ruolo + "\"}"));
	}

	// --- lista ---

	@Test
	void listaPaginataDalPiuRecenteSoloAttivi() throws Exception {
		salva("Carla", "Bianchi", "carla@example.com", Ruolo.UTENTE, Instant.parse("2026-03-01T10:00:00Z"));
		salva("Bruno", "Verdi", "bruno@example.com", Ruolo.UTENTE, Instant.parse("2026-02-01T10:00:00Z"));
		Utente cancellato = salva("Utente", "Eliminato", "deleted-x@quackfit.local", Ruolo.UTENTE,
				Instant.parse("2026-04-01T10:00:00Z"));
		cancellato.setDeletedAt(Instant.parse("2026-05-01T10:00:00Z"));
		utenteRepository.save(cancellato);

		lista(admin, "?page=0&size=2")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenuto[*].nome", contains("Carla", "Bruno")))
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.dimensione").value(2))
				.andExpect(jsonPath("$.totaleElementi").value(4))
				.andExpect(jsonPath("$.totalePagine").value(2));
		lista(admin, "?page=1&size=2")
				.andExpect(jsonPath("$.contenuto[*].nome", contains("Anna", "Super")));
	}

	@Test
	void ogniUtenteHaIDatiDellaSpecESenzaPassword() throws Exception {
		cerca(admin, "anna")
				.andExpect(jsonPath("$.contenuto[0].id").value(admin.getId().toString()))
				.andExpect(jsonPath("$.contenuto[0].cognome").value("Admin"))
				.andExpect(jsonPath("$.contenuto[0].email").value("anna@example.com"))
				.andExpect(jsonPath("$.contenuto[0].ruolo").value("ADMIN"))
				.andExpect(jsonPath("$.contenuto[0].emailVerificata").value(true))
				.andExpect(jsonPath("$.contenuto[0].creatoAt").value("2026-01-01T10:00:00Z"))
				.andExpect(jsonPath("$.contenuto[0].password").doesNotExist());
	}

	@Test
	void senzaParametriPagina0EDimensione20() throws Exception {
		lista(admin, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.dimensione").value(20));
	}

	@Test
	void ricercaInNomeCognomeEmailSenzaMaiuscole() throws Exception {
		salva("Mario", "Rossi", "m.rossi@example.com", Ruolo.UTENTE, Instant.parse("2026-02-01T10:00:00Z"));
		salva("Luigi", "Mariotti", "luigi@example.com", Ruolo.UTENTE, Instant.parse("2026-02-02T10:00:00Z"));
		salva("Paolo", "Neri", "paolo.mario@example.com", Ruolo.UTENTE, Instant.parse("2026-02-03T10:00:00Z"));
		salva("Gina", "Gialli", "gina@example.com", Ruolo.UTENTE, Instant.parse("2026-02-04T10:00:00Z"));

		cerca(admin, " MARIO ")
				.andExpect(jsonPath("$.contenuto[*].nome", containsInAnyOrder("Mario", "Luigi", "Paolo")));
	}

	@Test
	void jollyDelLikeCercatiComeTesto() throws Exception {
		salva("Gina", "Gialli", "gina_g@example.com", Ruolo.UTENTE, Instant.parse("2026-02-04T10:00:00Z"));
		salva("Gino", "Gialli", "ginoxg@example.com", Ruolo.UTENTE, Instant.parse("2026-02-05T10:00:00Z"));

		// "_" non è un jolly: trova solo gina_g, non ginoxg
		cerca(admin, "a_g").andExpect(jsonPath("$.contenuto[*].nome", contains("Gina")));
		// "%" non è un jolly: nessuno ha un "%" nei dati
		cerca(admin, "%").andExpect(jsonPath("$.totaleElementi").value(0));
	}

	@Test
	void parametriFuoriLimite400ConIlCampo() throws Exception {
		lista(admin, "?size=0").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.size").value("La dimensione deve essere tra 1 e 50"));
		lista(admin, "?size=51").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.size").value("La dimensione deve essere tra 1 e 50"));
		lista(admin, "?page=-1").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.page").value("La pagina non può essere negativa"));
		lista(admin, "?size=50").andExpect(status().isOk());
	}

	@Test
	void parametriNonNumerici400ConIlCampo() throws Exception {
		lista(admin, "?page=abc").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.page").exists());
		lista(admin, "?size=abc").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.size").exists());
	}

	// --- cambio ruolo ---

	@Test
	void promozioneRiuscitaELogoutOvunqueDelSoloBersaglio() throws Exception {
		Utente mario = salva("Mario", "Rossi", "mario@example.com", Ruolo.UTENTE, Instant.parse("2026-02-01T10:00:00Z"));
		String tokenMario = token(mario);

		cambiaRuolo(admin, mario, "ADMIN").andExpect(status().isNoContent());

		assertThat(utenteRepository.findById(mario.getId()).orElseThrow().getRuolo()).isEqualTo(Ruolo.ADMIN);
		// il token di Mario non vale più (dovrà rifare il login, e avrà il nuovo ruolo); quello dell'admin sì
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, tokenMario))
				.andExpect(status().isUnauthorized());
		lista(admin, "").andExpect(status().isOk());
	}

	@Test
	void unAdminNonRetrocedeUnAdmin403() throws Exception {
		Utente altroAdmin = salva("Bea", "Admin", "bea@example.com", Ruolo.ADMIN, Instant.parse("2026-02-01T10:00:00Z"));

		cambiaRuolo(admin, altroAdmin, "UTENTE")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("ACCESSO_NEGATO"));
		cambiaRuolo(superadmin, altroAdmin, "UTENTE").andExpect(status().isNoContent());
	}

	@Test
	void stessoRuolo204ETokenAncoraValido() throws Exception {
		Utente mario = salva("Mario", "Rossi", "mario@example.com", Ruolo.UTENTE, Instant.parse("2026-02-01T10:00:00Z"));
		streakRepository.save(new Streak(mario, Instant.parse("2026-02-01T10:00:00Z")));
		String tokenMario = token(mario);

		cambiaRuolo(admin, mario, "UTENTE").andExpect(status().isNoContent());

		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, tokenMario))
				.andExpect(status().isOk());
	}

	@Test
	void seStessoSuperadminEAssegnareSuperadmin403() throws Exception {
		Utente mario = salva("Mario", "Rossi", "mario@example.com", Ruolo.UTENTE, Instant.parse("2026-02-01T10:00:00Z"));

		cambiaRuolo(admin, admin, "UTENTE").andExpect(status().isForbidden());
		cambiaRuolo(admin, superadmin, "ADMIN").andExpect(status().isForbidden());
		cambiaRuolo(superadmin, mario, "SUPERADMIN").andExpect(status().isForbidden());
		assertThat(utenteRepository.findById(superadmin.getId()).orElseThrow().getRuolo()).isEqualTo(Ruolo.SUPERADMIN);
	}

	@Test
	void utenteInesistente404RuoloNonValido400() throws Exception {
		mockMvc.perform(patch("/api/admin/users/" + java.util.UUID.randomUUID() + "/role")
						.header(HttpHeaders.AUTHORIZATION, token(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ruolo\": \"ADMIN\"}"))
				.andExpect(status().isNotFound());
		Utente mario = salva("Mario", "Rossi", "mario@example.com", Ruolo.UTENTE, Instant.parse("2026-02-01T10:00:00Z"));
		cambiaRuolo(admin, mario, "PIPPO").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"));
		mockMvc.perform(patch("/api/admin/users/" + mario.getId() + "/role")
						.header(HttpHeaders.AUTHORIZATION, token(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campi.ruolo").value("Il ruolo è obbligatorio"));
	}

	// --- accessi ---

	@Test
	void unUtenteNormale403EUnAdminNonVerificato403() throws Exception {
		Utente mario = salva("Mario", "Rossi", "mario@example.com", Ruolo.UTENTE, Instant.parse("2026-02-01T10:00:00Z"));
		lista(mario, "").andExpect(status().isForbidden()).andExpect(jsonPath("$.codice").value("ACCESSO_NEGATO"));

		admin.setEmailVerificata(false);
		utenteRepository.save(admin);
		lista(admin, "").andExpect(status().isForbidden()).andExpect(jsonPath("$.codice").value("EMAIL_NON_VERIFICATA"));
	}

}
