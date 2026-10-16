package it.quackfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.ClockConfig;
import it.quackfit.common.CodiceErrore;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/** Unit test di RecuperoPasswordService con Mockito e Clock fisso (vedi auth.md, Password dimenticata e Reimpostazione). */
class RecuperoPasswordServiceTest {

	private static final Instant ADESSO = Instant.parse("2026-10-16T10:00:00Z");
	private static final String EMAIL = "mario@example.com";

	/** Forza bassa: stessa logica di BCrypt, test più veloci. */
	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

	private UtenteRepository utenteRepository;
	private CodiceService codiceService;
	private RecuperoPasswordService servizio;
	private Utente utente;

	@BeforeEach
	void setUp() {
		utenteRepository = mock(UtenteRepository.class);
		codiceService = mock(CodiceService.class);
		servizio = new RecuperoPasswordService(utenteRepository, codiceService, encoder,
				Clock.fixed(ADESSO, ClockConfig.FUSO));
		utente = new Utente("Mario", "Rossi", EMAIL, encoder.encode("Password1!"), ADESSO.minus(Duration.ofDays(1)));
		ReflectionTestUtils.setField(utente, "id", UUID.randomUUID());
	}

	private static ReimpostaPasswordRequest richiesta(String codice) {
		return new ReimpostaPasswordRequest(EMAIL, codice, "Nuova123!");
	}

	private static void codiceNonValido(Runnable azione) {
		assertThatThrownBy(azione::run)
				.isInstanceOf(ApplicazioneException.class)
				.hasMessage("Codice non valido o scaduto: richiedine uno nuovo")
				.satisfies(e -> assertThat(((ApplicazioneException) e).getCodice()).isEqualTo(CodiceErrore.CODICE_NON_VALIDO));
	}

	// --- password dimenticata ---

	@Test
	void utenteEsistenteGeneraEInviaIlCodice() {
		when(utenteRepository.findByEmailAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(utente));
		when(codiceService.reinvioConsentito(utente, TipoCodice.RESET_PASSWORD)).thenReturn(true);

		servizio.inviaCodice(EMAIL);

		verify(codiceService).generaEInvia(utente, TipoCodice.RESET_PASSWORD, null);
	}

	@Test
	void utenteInesistenteNonFaNulla() {
		when(utenteRepository.findByEmailAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.empty());

		servizio.inviaCodice(EMAIL);

		verify(codiceService, never()).generaEInvia(any(), any(), any());
	}

	@Test
	void primaDei60SecondiNonFaNullaENonLancia429() {
		when(utenteRepository.findByEmailAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(utente));
		when(codiceService.reinvioConsentito(utente, TipoCodice.RESET_PASSWORD)).thenReturn(false);

		servizio.inviaCodice(EMAIL);

		verify(codiceService, never()).generaEInvia(any(), any(), any());
	}

	// --- reimpostazione ---

	@Test
	void codiceGiustoAggiornaPasswordEmailTokenEContatori() {
		utente.setLoginFalliti(4);
		utente.setPrimoLoginFallitoAt(ADESSO.minus(Duration.ofMinutes(1)));
		utente.setBloccatoFino(ADESSO.plus(Duration.ofMinutes(10)));
		when(utenteRepository.findPerLoginByEmailAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(utente));

		servizio.reimposta(richiesta("123456"));

		verify(codiceService).verifica(utente, TipoCodice.RESET_PASSWORD, "123456");
		assertThat(encoder.matches("Nuova123!", utente.getPassword())).isTrue();
		assertThat(utente.isEmailVerificata()).isTrue();
		assertThat(utente.getTokenValidiDal()).isEqualTo(ADESSO);
		assertThat(utente.getLoginFalliti()).isZero();
		assertThat(utente.getPrimoLoginFallitoAt()).isNull();
		assertThat(utente.getBloccatoFino()).isNull();
	}

	@Test
	void emailInesistenteConfrontoFittizioECodiceNonValido() {
		when(utenteRepository.findPerLoginByEmailAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.empty());

		codiceNonValido(() -> servizio.reimposta(richiesta("123456")));

		verify(codiceService).confrontoFittizio("123456");
		verify(codiceService, never()).verifica(any(), any(), any());
	}

	/** Qualunque errore della verifica diventa lo stesso CODICE_NON_VALIDO, e l'utente non cambia. */
	@ParameterizedTest
	@EnumSource(value = CodiceErrore.class, names = { "CODICE_NON_VALIDO", "CODICE_SCADUTO", "TROPPI_TENTATIVI" })
	void ogniErroreDelCodiceDiventaCodiceNonValido(CodiceErrore errore) {
		String passwordPrima = utente.getPassword();
		Instant tokenPrima = utente.getTokenValidiDal();
		when(utenteRepository.findPerLoginByEmailAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(utente));
		when(codiceService.verifica(utente, TipoCodice.RESET_PASSWORD, "123456"))
				.thenThrow(new ApplicazioneException(errore, "messaggio di CodiceService"));

		codiceNonValido(() -> servizio.reimposta(richiesta("123456")));

		assertThat(utente.getPassword()).isEqualTo(passwordPrima);
		assertThat(utente.getTokenValidiDal()).isEqualTo(tokenPrima);
		assertThat(utente.isEmailVerificata()).isFalse();
	}

}
