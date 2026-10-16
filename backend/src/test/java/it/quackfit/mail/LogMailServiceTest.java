package it.quackfit.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Servizio su log: scrive il codice, e non esiste nel profilo prod. */
@ExtendWith(OutputCaptureExtension.class)
class LogMailServiceTest {

	private final ApplicationContextRunner contesto = new ApplicationContextRunner()
			.withBean(ComposizioneEmail.class, () -> mock(ComposizioneEmail.class))
			.withUserConfiguration(LogMailService.class)
			.withPropertyValues("quackfit.mail.modalita=log");

	@Test
	void scriveDestinatarioOggettoECodiceNelLog(CapturedOutput output) {
		ComposizioneEmail composizione = mock(ComposizioneEmail.class);
		when(composizione.resetPassword("mario@example.com", "Mario", "246810"))
				.thenReturn(new Email("mario@example.com", "Reimposta la password di QuackFit", "<p>html</p>", "246810"));

		new LogMailService(composizione).inviaResetPassword("mario@example.com", "Mario", "246810");

		assertThat(output)
				.contains("[EMAIL SOLO NEL LOG] a mario@example.com")
				.contains("Reimposta la password di QuackFit")
				.contains("codice: 246810");
	}

	@Test
	void attivoInLocaleConModalitaLog() {
		contesto.run(ctx -> assertThat(ctx).hasSingleBean(MailService.class).hasSingleBean(LogMailService.class));
	}

	@Test
	void maiNelProfiloProdAncheConModalitaLog() {
		contesto.withPropertyValues("spring.profiles.active=prod")
				.run(ctx -> assertThat(ctx).doesNotHaveBean(MailService.class));
	}

	@Test
	void nonAttivoConUnAltraModalita() {
		contesto.withPropertyValues("quackfit.mail.modalita=brevo")
				.run(ctx -> assertThat(ctx).doesNotHaveBean(LogMailService.class));
	}

}
