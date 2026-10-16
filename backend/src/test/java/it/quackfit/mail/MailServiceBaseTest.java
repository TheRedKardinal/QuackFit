package it.quackfit.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Comportamento comune a tutte le implementazioni: nessuna eccezione al chiamante
 * e nel log di errore mai il codice né l'indirizzo completo.
 */
@ExtendWith(OutputCaptureExtension.class)
class MailServiceBaseTest {

	private static final String DESTINATARIO = "mario.rossi@example.com";
	private static final String CODICE = "987654";

	/** Implementazione finta che fallisce sempre con l'eccezione scelta dal test. */
	private static MailService cheFallisceCon(RuntimeException errore) {
		ComposizioneEmail composizione = mock(ComposizioneEmail.class);
		when(composizione.verificaEmail(DESTINATARIO, "Mario", CODICE))
				.thenReturn(new Email(DESTINATARIO, "Oggetto", "<p>" + CODICE + "</p>", CODICE));
		return new MailServiceBase(composizione) {
			@Override
			protected void consegna(Email email) {
				throw errore;
			}
		};
	}

	@Test
	void eccezioneDiTerziNonArrivaAlChiamanteENelLogNonCeNeCodiceNeIndirizzo(CapturedOutput output) {
		// es. un errore SMTP il cui messaggio contiene l'indirizzo e il corpo dell'email
		MailService servizio = cheFallisceCon(
				new IllegalStateException("Invalid Addresses: " + DESTINATARIO + " body " + CODICE));

		assertThatCode(() -> servizio.inviaVerificaEmail(DESTINATARIO, "Mario", CODICE)).doesNotThrowAnyException();

		assertThat(output)
				.contains("Invio dell'email a m***@example.com non riuscito: IllegalStateException")
				.doesNotContain(CODICE)
				.doesNotContain(DESTINATARIO);
	}

	@Test
	void eccezioneNostraRegistraIlSuoMessaggio(CapturedOutput output) {
		MailService servizio = cheFallisceCon(new InvioEmailFallitoException("Brevo ha risposto con lo stato HTTP 500"));

		assertThatCode(() -> servizio.inviaVerificaEmail(DESTINATARIO, "Mario", CODICE)).doesNotThrowAnyException();

		assertThat(output)
				.contains("m***@example.com non riuscito: Brevo ha risposto con lo stato HTTP 500")
				.doesNotContain(CODICE);
	}

	@Test
	void mascheraEmail() {
		assertThat(MailServiceBase.mascheraEmail("mario.rossi@example.com")).isEqualTo("m***@example.com");
		assertThat(MailServiceBase.mascheraEmail("a@b.it")).isEqualTo("a***@b.it");
		assertThat(MailServiceBase.mascheraEmail("senza-chiocciola")).isEqualTo("***");
		assertThat(MailServiceBase.mascheraEmail("@example.com")).isEqualTo("***");
		assertThat(MailServiceBase.mascheraEmail(null)).isEqualTo("***");
	}

}
