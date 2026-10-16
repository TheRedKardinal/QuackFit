package it.quackfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.ClockConfig;
import it.quackfit.common.CodiceErrore;
import it.quackfit.security.JwtService;
import it.quackfit.utente.StreakRepository;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/** Unit test del login: tutti i casi della sezione "Contatori dei login falliti" di auth.md. */
class LoginServiceTest {

	private static final String EMAIL = "mario@example.com";
	private static final String PASSWORD = "Password1!";
	private static final String SBAGLIATA = "Sbagliata1!";

	/** Clock fisso che il test può far avanzare. */
	private static class OrologioRegolabile extends Clock {

		private Instant adesso = Instant.parse("2026-10-16T10:00:00Z");

		void avanza(Duration durata) {
			adesso = adesso.plus(durata);
		}

		@Override
		public Instant instant() {
			return adesso;
		}

		@Override
		public ZoneId getZone() {
			return ClockConfig.FUSO;
		}

		@Override
		public Clock withZone(ZoneId zona) {
			return this;
		}

	}

	private OrologioRegolabile orologio;
	private PasswordEncoder encoder;
	private UtenteRepository utenteRepository;
	private AuthService servizio;
	private Utente utente;

	@BeforeEach
	void setUp() {
		orologio = new OrologioRegolabile();
		// spy: un BCrypt vero (costo basso, test veloci) di cui si possono verificare le chiamate
		encoder = spy(new BCryptPasswordEncoder(4));
		utenteRepository = mock(UtenteRepository.class);
		JwtService jwtService = mock(JwtService.class);
		when(jwtService.genera(any(Utente.class))).thenReturn("token-di-prova");
		servizio = new AuthService(utenteRepository, mock(StreakRepository.class), mock(CodiceService.class), encoder,
				jwtService, orologio);

		utente = new Utente("Mario", "Rossi", EMAIL, encoder.encode(PASSWORD), orologio.instant().minus(Duration.ofDays(1)));
		ReflectionTestUtils.setField(utente, "id", UUID.randomUUID());
		when(utenteRepository.findPerLoginByEmailAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(utente));
		clearInvocations(encoder);
	}

	private TokenResponse login(String password) {
		return servizio.login(new LoginRequest(EMAIL, password));
	}

	private static void credenzialiNonValide(Runnable azione) {
		assertThatThrownBy(azione::run)
				.isInstanceOf(ApplicazioneException.class)
				.hasMessage("Email o password non corretti, oppure troppi tentativi: riprova tra qualche minuto.")
				.satisfies(e -> assertThat(((ApplicazioneException) e).getCodice())
						.isEqualTo(CodiceErrore.CREDENZIALI_NON_VALIDE));
	}

	/** Errore di login dopo aver fatto avanzare l'orologio. */
	private void erroreDopo(Duration attesa) {
		orologio.avanza(attesa);
		credenzialiNonValide(() -> login(SBAGLIATA));
	}

	private void assertNonBloccato() {
		assertThat(utente.getBloccatoFino()).isNull();
	}

	private void assertBloccatoFinoA(Instant fine) {
		assertThat(utente.getBloccatoFino()).isEqualTo(fine);
		assertThat(utente.getLoginFalliti()).isZero();
		assertThat(utente.getPrimoLoginFallitoAt()).isNull();
	}

	// --- login riuscito ---

	@Test
	void credenzialiGiusteRestituisconoIlToken() {
		assertThat(login(PASSWORD).token()).isEqualTo("token-di-prova");
	}

	@Test
	void ammessoAncheConEmailNonVerificata() {
		assertThat(utente.isEmailVerificata()).isFalse();

		assertThat(login(PASSWORD).token()).isNotBlank();
	}

	@Test
	void loginRiuscitoAzzeraIContatori() {
		erroreDopo(Duration.ZERO);
		erroreDopo(Duration.ofSeconds(20));
		assertThat(utente.getLoginFalliti()).isEqualTo(2);

		login(PASSWORD);

		assertThat(utente.getLoginFalliti()).isZero();
		assertThat(utente.getPrimoLoginFallitoAt()).isNull();
		assertNonBloccato();
	}

	// --- contatori ---

	@Test
	void primoErroreApreUnaSerie() {
		Instant adesso = orologio.instant();

		erroreDopo(Duration.ZERO);

		assertThat(utente.getLoginFalliti()).isEqualTo(1);
		assertThat(utente.getPrimoLoginFallitoAt()).isEqualTo(adesso);
		assertNonBloccato();
	}

	@Test
	void treErroriEntro10SecondiBloccano() {
		erroreDopo(Duration.ZERO);
		erroreDopo(Duration.ofSeconds(5));
		erroreDopo(Duration.ofSeconds(5)); // 10 secondi dal primo: ancora "da bot"

		assertBloccatoFinoA(orologio.instant().plus(Duration.ofMinutes(15)));
	}

	@Test
	void treErroriInPiuDi10SecondiNonBloccano() {
		erroreDopo(Duration.ZERO);
		erroreDopo(Duration.ofSeconds(6));
		erroreDopo(Duration.ofSeconds(5)); // 11 secondi dal primo

		assertThat(utente.getLoginFalliti()).isEqualTo(3);
		assertNonBloccato();
	}

	@Test
	void cinqueErroriBloccano() {
		for (int i = 0; i < 4; i++) {
			erroreDopo(Duration.ofSeconds(30));
		}
		assertThat(utente.getLoginFalliti()).isEqualTo(4);
		assertNonBloccato();

		erroreDopo(Duration.ofSeconds(30));

		assertBloccatoFinoA(orologio.instant().plus(Duration.ofMinutes(15)));
	}

	@Test
	void dopo15MinutiDalPrimoErroreLaSerieRiparte() {
		erroreDopo(Duration.ZERO);
		erroreDopo(Duration.ofMinutes(5));
		erroreDopo(Duration.ofMinutes(10)); // esattamente 15 minuti: stessa serie
		assertThat(utente.getLoginFalliti()).isEqualTo(3);

		erroreDopo(Duration.ofSeconds(1)); // 15 minuti e 1 secondo: nuova serie

		assertThat(utente.getLoginFalliti()).isEqualTo(1);
		assertThat(utente.getPrimoLoginFallitoAt()).isEqualTo(orologio.instant());
	}

	@Test
	void bloccatoConLaPasswordGiusta401ESenzaToccareIContatori() {
		Instant fine = orologio.instant().plus(Duration.ofMinutes(10));
		utente.setBloccatoFino(fine);

		credenzialiNonValide(() -> login(PASSWORD));

		assertThat(utente.getBloccatoFino()).isEqualTo(fine);
		assertThat(utente.getLoginFalliti()).isZero();
		// la password vera non si controlla
		verify(encoder, never()).matches(anyString(), eq(utente.getPassword()));
	}

	@Test
	void bloccatoConLaPasswordSbagliataNonContaErrori() {
		utente.setBloccatoFino(orologio.instant().plus(Duration.ofMinutes(10)));

		credenzialiNonValide(() -> login(SBAGLIATA));

		assertThat(utente.getLoginFalliti()).isZero();
		assertThat(utente.getPrimoLoginFallitoAt()).isNull();
	}

	@Test
	void bloccoScadutoSiPuoEntrare() {
		erroreDopo(Duration.ZERO);
		erroreDopo(Duration.ofSeconds(1));
		erroreDopo(Duration.ofSeconds(1));
		assertThat(utente.getBloccatoFino()).isNotNull();

		orologio.avanza(Duration.ofMinutes(15));

		assertThat(login(PASSWORD).token()).isNotBlank();
		assertNonBloccato();
	}

	// --- stesso messaggio e stessi tempi ---

	@Test
	void emailInesistenteStessoMessaggioEConfrontoConLHashFittizio() {
		when(utenteRepository.findPerLoginByEmailAndDeletedAtIsNull("nessuno@example.com")).thenReturn(Optional.empty());

		credenzialiNonValide(() -> servizio.login(new LoginRequest("nessuno@example.com", PASSWORD)));

		verify(encoder).matches(eq(PASSWORD), anyString());
	}

	@Test
	void bloccatoConfrontoConLHashFittizioNonConQuelloVero() {
		utente.setBloccatoFino(orologio.instant().plus(Duration.ofMinutes(10)));

		credenzialiNonValide(() -> login(PASSWORD));

		verify(encoder).matches(eq(PASSWORD), argThat(hash -> !hash.equals(utente.getPassword())));
	}

	// --- password oltre i 72 byte ---

	@Test
	void passwordDi73ByteContaComeSbagliataSenzaChiamareBCrypt() {
		String da73 = "Aa1!" + "a".repeat(69);

		credenzialiNonValide(() -> login(da73));

		assertThat(utente.getLoginFalliti()).isEqualTo(1);
		verify(encoder, never()).matches(anyString(), anyString());
	}

	@Test
	void passwordDi73ByteConEmailInesistenteNienteBCrypt() {
		when(utenteRepository.findPerLoginByEmailAndDeletedAtIsNull("nessuno@example.com")).thenReturn(Optional.empty());

		credenzialiNonValide(() -> servizio.login(new LoginRequest("nessuno@example.com", "Aa1!" + "a".repeat(69))));

		verify(encoder, never()).matches(anyString(), anyString());
	}

	@Test
	void lEmailVienePulita() {
		assertThat(new LoginRequest("  Mario@Example.COM ", PASSWORD).email()).isEqualTo(EMAIL);
		assertThat(new LoginRequest(EMAIL, " Password1! ").password()).isEqualTo(" Password1! ");
	}

}
