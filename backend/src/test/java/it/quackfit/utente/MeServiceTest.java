package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;
import it.quackfit.tentativi.TentativoLezioneRepository;

/**
 * Unit test di MeService con Mockito, un orologio fisso e un BCrypt vero ma veloce (costo 4).
 * Adesso = 17 ottobre 2026 alle 00:30 di Roma (16 ottobre 22:30 UTC): l'orologio è in UTC apposta,
 * così si verifica che "oggi" e "ieri" si calcolano sul fuso di Roma e non su quello dell'orologio.
 */
class MeServiceTest {

	private static final Instant ADESSO = Instant.parse("2026-10-16T22:30:00Z");
	private static final LocalDate OGGI = LocalDate.of(2026, 10, 17);

	private static final String PASSWORD = "Password1!";
	private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

	private UtenteRepository utenteRepository;
	private StreakRepository streakRepository;
	private TentativoLezioneRepository tentativoRepository;
	private MeService servizio;
	private Utente utente;
	private Streak streak;

	@BeforeEach
	void setUp() {
		utenteRepository = mock(UtenteRepository.class);
		streakRepository = mock(StreakRepository.class);
		tentativoRepository = mock(TentativoLezioneRepository.class);
		servizio = new MeService(utenteRepository, streakRepository, tentativoRepository, ENCODER,
				Clock.fixed(ADESSO, ZoneOffset.UTC));
		utente = new Utente("Mario", "Rossi", "mario@example.com", ENCODER.encode(PASSWORD),
				Instant.parse("2026-01-01T10:00:00Z"));
		ReflectionTestUtils.setField(utente, "id", UUID.randomUUID());
		when(utenteRepository.findById(utente.getId())).thenReturn(Optional.of(utente));
		streak = new Streak(utente, Instant.parse("2026-01-01T10:00:00Z"));
		streak.setValore(5);
		streak.setRecord(8);
		when(streakRepository.findByUtente(utente)).thenReturn(Optional.of(streak));
		when(tentativoRepository.lezioniCompletate(utente.getId())).thenReturn(List.of());
	}

	@Test
	void datiDelProfilo() {
		utente.setFotoProfilo("https://example.com/foto.webp");
		streak.setUltimaAttivita(OGGI);
		when(tentativoRepository.lezioniCompletate(utente.getId()))
				.thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

		MeResponse risposta = servizio.profilo(utente);

		assertThat(risposta).isEqualTo(new MeResponse("Mario", "Rossi", "mario@example.com", Ruolo.UTENTE,
				"https://example.com/foto.webp", false, new StreakResponse(5, 8), 2));
	}

	// --- streak calcolata in lettura ---

	@Test
	void ultimaAttivitaOggiMostraIlValoreSalvato() {
		streak.setUltimaAttivita(OGGI);

		assertThat(servizio.profilo(utente).streak()).isEqualTo(new StreakResponse(5, 8));
	}

	@Test
	void ultimaAttivitaIeriMostraIlValoreSalvato() {
		streak.setUltimaAttivita(OGGI.minusDays(1));

		assertThat(servizio.profilo(utente).streak()).isEqualTo(new StreakResponse(5, 8));
	}

	@Test
	void ultimaAttivitaPiuVecchiaDiIeriMostraZeroEIlRecordResta() {
		streak.setUltimaAttivita(OGGI.minusDays(2));

		assertThat(servizio.profilo(utente).streak()).isEqualTo(new StreakResponse(0, 8));
	}

	@Test
	void nessunaAttivitaMostraZero() {
		streak.setValore(0);
		streak.setRecord(0);

		assertThat(servizio.profilo(utente).streak()).isEqualTo(new StreakResponse(0, 0));
	}

	@Test
	void laLetturaNonModificaLaStreakSalvata() {
		streak.setUltimaAttivita(OGGI.minusDays(5));

		servizio.profilo(utente);

		assertThat(streak.getValore()).isEqualTo(5);
		assertThat(streak.getRecord()).isEqualTo(8);
		assertThat(streak.getUltimaAttivita()).isEqualTo(OGGI.minusDays(5));
		verify(streakRepository, never()).save(ArgumentMatchers.any());
	}

	// --- dato incoerente ---

	@Test
	void streakMancanteErroreConLIdDellUtente() {
		when(streakRepository.findByUtente(utente)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servizio.profilo(utente))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(utente.getId().toString())
				.hasMessageNotContaining(utente.getEmail());
	}

	// --- modifica dati ---

	@Test
	void modificaDatiSalvaNomeECognome() {
		servizio.modificaDati(utente.getId(), new ModificaDatiRequest("Luigi", "Verdi"));

		assertThat(utente.getNome()).isEqualTo("Luigi");
		assertThat(utente.getCognome()).isEqualTo("Verdi");
		assertThat(utente.getEmail()).isEqualTo("mario@example.com");
	}

	// --- cambio password ---

	@Test
	void cambioPasswordRiuscitoNuovoHashELogoutOvunque() {
		Instant tokenValidiDalPrima = utente.getTokenValidiDal();

		servizio.cambiaPassword(utente.getId(), new CambioPasswordRequest(PASSWORD, "Nuova123!"));

		assertThat(ENCODER.matches("Nuova123!", utente.getPassword())).isTrue();
		assertThat(utente.getTokenValidiDal()).isEqualTo(ADESSO).isAfter(tokenValidiDalPrima);
	}

	@Test
	void passwordAttualeSbagliataNonCambiaNulla() {
		String hashPrima = utente.getPassword();

		assertThatThrownBy(() -> servizio.cambiaPassword(utente.getId(), new CambioPasswordRequest("Sbagliata1!", "Nuova123!")))
				.isInstanceOfSatisfying(ApplicazioneException.class,
						e -> assertThat(e.getCodice()).isEqualTo(CodiceErrore.PASSWORD_ATTUALE_ERRATA));
		assertThat(utente.getPassword()).isEqualTo(hashPrima);
		assertThat(utente.getTokenValidiDal()).isNotEqualTo(ADESSO);
	}

	@Test
	void passwordAttualeOltre72ByteContaComeSbagliataSenzaEccezioniDiBcrypt() {
		String troppoLunga = "Aa1!" + "a".repeat(69); // 73 byte

		assertThatThrownBy(() -> servizio.cambiaPassword(utente.getId(), new CambioPasswordRequest(troppoLunga, "Nuova123!")))
				.isInstanceOfSatisfying(ApplicazioneException.class,
						e -> assertThat(e.getCodice()).isEqualTo(CodiceErrore.PASSWORD_ATTUALE_ERRATA));
	}

	@Test
	void nuovaUgualeAllAttualeErroreSulCampoDopoIlControlloDellAttuale() {
		assertThatThrownBy(() -> servizio.cambiaPassword(utente.getId(), new CambioPasswordRequest(PASSWORD, PASSWORD)))
				.isInstanceOfSatisfying(ApplicazioneException.class, e -> {
					assertThat(e.getCodice()).isEqualTo(CodiceErrore.VALIDAZIONE);
					assertThat(e.getCampi()).containsEntry("nuovaPassword", MeService.MSG_PASSWORD_UGUALE);
				});
		assertThat(utente.getTokenValidiDal()).isNotEqualTo(ADESSO);
	}

	@Test
	void conLAttualeSbagliataNonSiDiceSeLaNuovaEUgualeAllAttuale() {
		// la "nuova" è la password vera, ma l'attuale è sbagliata: l'errore resta quello della password attuale
		assertThatThrownBy(() -> servizio.cambiaPassword(utente.getId(), new CambioPasswordRequest("Sbagliata1!", PASSWORD)))
				.isInstanceOfSatisfying(ApplicazioneException.class,
						e -> assertThat(e.getCodice()).isEqualTo(CodiceErrore.PASSWORD_ATTUALE_ERRATA));
	}

}
