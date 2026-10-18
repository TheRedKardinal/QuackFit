package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;

/** Unit test del cambio ruolo con Mockito: ogni cella della tabella dei permessi di admin.md. */
class AdminUtentiServiceTest {

	private static final Instant CREAZIONE = Instant.parse("2026-01-01T10:00:00Z");
	private static final Instant ADESSO = Instant.parse("2026-10-18T10:00:00Z");

	private UtenteRepository utenteRepository;
	private AdminUtentiService servizio;

	@BeforeEach
	void setUp() {
		utenteRepository = mock(UtenteRepository.class);
		servizio = new AdminUtentiService(utenteRepository, Clock.fixed(ADESSO, ZoneOffset.UTC));
	}

	private Utente utente(Ruolo ruolo) {
		Utente utente = new Utente("Nome", "Cognome", UUID.randomUUID() + "@example.com", "hash", CREAZIONE);
		ReflectionTestUtils.setField(utente, "id", UUID.randomUUID());
		utente.setRuolo(ruolo);
		utente.setEmailVerificata(true);
		when(utenteRepository.findById(utente.getId())).thenReturn(Optional.of(utente));
		return utente;
	}

	private static void accessoNegato(Runnable azione) {
		assertThatThrownBy(azione::run).isInstanceOfSatisfying(ApplicazioneException.class,
				e -> assertThat(e.getCodice()).isEqualTo(CodiceErrore.ACCESSO_NEGATO));
	}

	// --- tabella dei permessi ---

	@ParameterizedTest(name = "{0} promuove UTENTE → ADMIN")
	@CsvSource({ "ADMIN", "SUPERADMIN" })
	void promozioneConsentitaAdAdminESuperadmin(Ruolo chi) {
		Utente bersaglio = utente(Ruolo.UTENTE);

		servizio.cambiaRuolo(utente(chi), bersaglio.getId(), Ruolo.ADMIN);

		assertThat(bersaglio.getRuolo()).isEqualTo(Ruolo.ADMIN);
		assertThat(bersaglio.getTokenValidiDal()).isEqualTo(ADESSO);
	}

	@Test
	void unAdminNonRetrocedeUnAdmin() {
		Utente bersaglio = utente(Ruolo.ADMIN);

		accessoNegato(() -> servizio.cambiaRuolo(utente(Ruolo.ADMIN), bersaglio.getId(), Ruolo.UTENTE));
		assertThat(bersaglio.getRuolo()).isEqualTo(Ruolo.ADMIN);
		assertThat(bersaglio.getTokenValidiDal()).isEqualTo(CREAZIONE);
	}

	@Test
	void ilSuperadminRetrocedeUnAdmin() {
		Utente bersaglio = utente(Ruolo.ADMIN);

		servizio.cambiaRuolo(utente(Ruolo.SUPERADMIN), bersaglio.getId(), Ruolo.UTENTE);

		assertThat(bersaglio.getRuolo()).isEqualTo(Ruolo.UTENTE);
		assertThat(bersaglio.getTokenValidiDal()).isEqualTo(ADESSO);
	}

	// --- controlli prima della tabella ---

	@Test
	void utenteInesistenteOCancellato404() {
		Utente cancellato = utente(Ruolo.UTENTE);
		cancellato.setDeletedAt(CREAZIONE);
		Utente admin = utente(Ruolo.ADMIN);

		for (UUID id : new UUID[] { UUID.randomUUID(), cancellato.getId() }) {
			assertThatThrownBy(() -> servizio.cambiaRuolo(admin, id, Ruolo.ADMIN))
					.isInstanceOfSatisfying(ApplicazioneException.class,
							e -> assertThat(e.getCodice()).isEqualTo(CodiceErrore.NON_TROVATO));
		}
	}

	@ParameterizedTest(name = "{0} non assegna SUPERADMIN")
	@CsvSource({ "ADMIN", "SUPERADMIN" })
	void nessunoAssegnaSuperadmin(Ruolo chi) {
		Utente bersaglio = utente(Ruolo.UTENTE);

		accessoNegato(() -> servizio.cambiaRuolo(utente(chi), bersaglio.getId(), Ruolo.SUPERADMIN));
	}

	@ParameterizedTest(name = "{0} non cambia il proprio ruolo in {1}")
	@CsvSource({ "ADMIN,UTENTE", "ADMIN,ADMIN", "SUPERADMIN,ADMIN", "SUPERADMIN,UTENTE" })
	void nessunoCambiaIlProprioRuolo(Ruolo chi, Ruolo nuovo) {
		Utente chiamante = utente(chi);

		accessoNegato(() -> servizio.cambiaRuolo(chiamante, chiamante.getId(), nuovo));
		assertThat(chiamante.getRuolo()).isEqualTo(chi);
	}

	@ParameterizedTest(name = "{0} non tocca un SUPERADMIN")
	@CsvSource({ "ADMIN", "SUPERADMIN" })
	void ilSuperadminEIntoccabile(Ruolo chi) {
		Utente superadmin = utente(Ruolo.SUPERADMIN);

		accessoNegato(() -> servizio.cambiaRuolo(utente(chi), superadmin.getId(), Ruolo.UTENTE));
		assertThat(superadmin.getRuolo()).isEqualTo(Ruolo.SUPERADMIN);
	}

	@ParameterizedTest(name = "{0} → {1} già uguale: nessuna modifica")
	@CsvSource({ "ADMIN,UTENTE,UTENTE", "ADMIN,ADMIN,ADMIN", "SUPERADMIN,ADMIN,ADMIN" })
	void stessoRuoloNessunLogout(Ruolo chi, Ruolo attuale, Ruolo nuovo) {
		Utente bersaglio = utente(attuale);

		servizio.cambiaRuolo(utente(chi), bersaglio.getId(), nuovo);

		assertThat(bersaglio.getRuolo()).isEqualTo(attuale);
		assertThat(bersaglio.getTokenValidiDal()).isEqualTo(CREAZIONE);
	}

	// --- ricerca ---

	@Test
	void modelloLikeInMinuscoloConIJollyProtetti() {
		assertThat(AdminUtentiService.modelloLike("Mario")).isEqualTo("%mario%");
		assertThat(AdminUtentiService.modelloLike("100%_ok!")).isEqualTo("%100!%!_ok!!%");
	}

}
