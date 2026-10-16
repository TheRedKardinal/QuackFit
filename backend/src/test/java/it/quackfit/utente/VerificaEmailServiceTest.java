package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import it.quackfit.auth.CodiceService;
import it.quackfit.auth.TipoCodice;
import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;

/** Unit test di VerificaEmailService con Mockito. */
class VerificaEmailServiceTest {

	private UtenteRepository utenteRepository;
	private CodiceService codiceService;
	private VerificaEmailService servizio;
	private Utente utente;

	@BeforeEach
	void setUp() {
		utenteRepository = mock(UtenteRepository.class);
		codiceService = mock(CodiceService.class);
		servizio = new VerificaEmailService(utenteRepository, codiceService);
		utente = new Utente("Mario", "Rossi", "mario@example.com", "hash", Instant.parse("2026-01-01T10:00:00Z"));
		ReflectionTestUtils.setField(utente, "id", UUID.randomUUID());
		when(utenteRepository.findById(utente.getId())).thenReturn(Optional.of(utente));
	}

	// --- verifica ---

	@Test
	void codiceGiustoVerificaLEmail() {
		servizio.verifica(utente.getId(), "123456");

		verify(codiceService).verifica(utente, TipoCodice.VERIFICA_EMAIL, "123456");
		assertThat(utente.isEmailVerificata()).isTrue();
	}

	@Test
	void codiceSbagliatoLasciaPassareLErroreELEmailNonVerificata() {
		doThrow(new ApplicazioneException(CodiceErrore.CODICE_NON_VALIDO, "Codice non valido"))
				.when(codiceService).verifica(utente, TipoCodice.VERIFICA_EMAIL, "000000");

		assertThatThrownBy(() -> servizio.verifica(utente.getId(), "000000"))
				.isInstanceOf(ApplicazioneException.class)
				.hasMessage("Codice non valido");
		assertThat(utente.isEmailVerificata()).isFalse();
	}

	@Test
	void emailGiaVerificataNessunControlloDelCodice() {
		utente.setEmailVerificata(true);

		servizio.verifica(utente.getId(), "000000");

		verifyNoInteractions(codiceService);
		assertThat(utente.isEmailVerificata()).isTrue();
	}

	// --- reinvio ---

	@Test
	void reinvioGeneraUnNuovoCodice() {
		servizio.reinvia(utente.getId());

		verify(codiceService).generaEInvia(utente, TipoCodice.VERIFICA_EMAIL, null);
	}

	@Test
	void reinvioConEmailGiaVerificataNonInviaNulla() {
		utente.setEmailVerificata(true);

		servizio.reinvia(utente.getId());

		verifyNoInteractions(codiceService);
	}

	@Test
	void reinvioEntro60SecondiLasciaPassareIl429() {
		doThrow(new ApplicazioneException(CodiceErrore.ATTENDI_REINVIO, "Attendi un minuto"))
				.when(codiceService).generaEInvia(utente, TipoCodice.VERIFICA_EMAIL, null);

		assertThatThrownBy(() -> servizio.reinvia(utente.getId()))
				.isInstanceOf(ApplicazioneException.class)
				.satisfies(e -> assertThat(((ApplicazioneException) e).getCodice()).isEqualTo(CodiceErrore.ATTENDI_REINVIO));
	}

}
