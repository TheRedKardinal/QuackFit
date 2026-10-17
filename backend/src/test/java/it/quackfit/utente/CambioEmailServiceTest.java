package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import it.quackfit.auth.CodiceService;
import it.quackfit.auth.CodiceVerificato;
import it.quackfit.auth.TipoCodice;
import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;

/** Unit test di CambioEmailService con Mockito, un orologio fisso e un BCrypt vero ma veloce (costo 4). */
class CambioEmailServiceTest {

	private static final Instant ADESSO = Instant.parse("2026-10-17T10:00:00Z");
	private static final String PASSWORD = "Password1!";
	private static final String NUOVA = "luigi@example.com";
	private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

	private UtenteRepository utenteRepository;
	private CodiceService codiceService;
	private CambioEmailService servizio;
	private Utente utente;

	@BeforeEach
	void setUp() {
		utenteRepository = mock(UtenteRepository.class);
		codiceService = mock(CodiceService.class);
		servizio = new CambioEmailService(utenteRepository, new PasswordAttuale(ENCODER), codiceService,
				Clock.fixed(ADESSO, ZoneOffset.UTC));
		utente = new Utente("Mario", "Rossi", "mario@example.com", ENCODER.encode(PASSWORD),
				Instant.parse("2026-01-01T10:00:00Z"));
		utente.setEmailVerificata(true);
		ReflectionTestUtils.setField(utente, "id", UUID.randomUUID());
		when(utenteRepository.findById(utente.getId())).thenReturn(Optional.of(utente));
	}

	private static void errore(Runnable azione, CodiceErrore codice) {
		assertThatThrownBy(azione::run)
				.isInstanceOfSatisfying(ApplicazioneException.class, e -> assertThat(e.getCodice()).isEqualTo(codice));
	}

	// --- richiesta ---

	@Test
	void richiestaValidaInviaIlCodiceAllaNuovaEmailSenzaCambiareQuellaAttuale() {
		servizio.richiedi(utente.getId(), new CambioEmailRequest(NUOVA, PASSWORD));

		verify(codiceService).generaEInvia(utente, TipoCodice.CAMBIO_EMAIL, NUOVA);
		assertThat(utente.getEmail()).isEqualTo("mario@example.com");
	}

	@Test
	void passwordSbagliataPrimaDiTutto() {
		// anche se l'email è già usata: senza la password giusta non si scopre
		when(utenteRepository.existsByEmail(NUOVA)).thenReturn(true);

		errore(() -> servizio.richiedi(utente.getId(), new CambioEmailRequest(NUOVA, "Sbagliata1!")),
				CodiceErrore.PASSWORD_ATTUALE_ERRATA);
		verify(utenteRepository, never()).existsByEmail(any());
		verifyNoInteractions(codiceService);
	}

	@Test
	void stessaEmailErroreSulCampo() {
		// il record pulisce l'input: con spazi e maiuscole è sempre la stessa email
		assertThatThrownBy(() -> servizio.richiedi(utente.getId(), new CambioEmailRequest("  Mario@Example.COM ", PASSWORD)))
				.isInstanceOfSatisfying(ApplicazioneException.class, e -> {
					assertThat(e.getCodice()).isEqualTo(CodiceErrore.VALIDAZIONE);
					assertThat(e.getCampi()).containsEntry("nuovaEmail", CambioEmailService.MSG_EMAIL_UGUALE);
				});
		verifyNoInteractions(codiceService);
	}

	@Test
	void emailGiaUsata409() {
		when(utenteRepository.existsByEmail(NUOVA)).thenReturn(true);

		errore(() -> servizio.richiedi(utente.getId(), new CambioEmailRequest(NUOVA, PASSWORD)), CodiceErrore.EMAIL_GIA_USATA);
		verifyNoInteractions(codiceService);
	}

	@Test
	void entro60SecondiAttendiReinvio() {
		doThrow(new ApplicazioneException(CodiceErrore.ATTENDI_REINVIO, "Attendi"))
				.when(codiceService).generaEInvia(utente, TipoCodice.CAMBIO_EMAIL, NUOVA);

		errore(() -> servizio.richiedi(utente.getId(), new CambioEmailRequest(NUOVA, PASSWORD)), CodiceErrore.ATTENDI_REINVIO);
	}

	// --- conferma ---

	@Test
	void confermaRiuscitaNuovaEmailVerificataELogoutOvunque() {
		utente.setEmailVerificata(false);
		when(codiceService.verifica(utente, TipoCodice.CAMBIO_EMAIL, "123456")).thenReturn(new CodiceVerificato(NUOVA));

		servizio.conferma(utente.getId(), "123456");

		assertThat(utente.getEmail()).isEqualTo(NUOVA);
		assertThat(utente.isEmailVerificata()).isTrue();
		assertThat(utente.getTokenValidiDal()).isEqualTo(ADESSO);
		verify(utenteRepository).saveAndFlush(utente);
	}

	@Test
	void codiceSbagliatoNessunaModifica() {
		when(codiceService.verifica(utente, TipoCodice.CAMBIO_EMAIL, "000000"))
				.thenThrow(new ApplicazioneException(CodiceErrore.CODICE_NON_VALIDO, "Codice non valido"));

		errore(() -> servizio.conferma(utente.getId(), "000000"), CodiceErrore.CODICE_NON_VALIDO);
		assertThat(utente.getEmail()).isEqualTo("mario@example.com");
		verify(utenteRepository, never()).saveAndFlush(any());
	}

	@Test
	void emailPresaNelFrattempo409() {
		when(codiceService.verifica(utente, TipoCodice.CAMBIO_EMAIL, "123456")).thenReturn(new CodiceVerificato(NUOVA));
		when(utenteRepository.existsByEmail(NUOVA)).thenReturn(true);

		errore(() -> servizio.conferma(utente.getId(), "123456"), CodiceErrore.EMAIL_GIA_USATA);
		assertThat(utente.getEmail()).isEqualTo("mario@example.com");
	}

	@Test
	void dueConfermeInsiemeIlVincoloUniqueDiventa409() {
		when(codiceService.verifica(utente, TipoCodice.CAMBIO_EMAIL, "123456")).thenReturn(new CodiceVerificato(NUOVA));
		when(utenteRepository.saveAndFlush(utente)).thenThrow(new DataIntegrityViolationException("uk email"));

		errore(() -> servizio.conferma(utente.getId(), "123456"), CodiceErrore.EMAIL_GIA_USATA);
	}

}
