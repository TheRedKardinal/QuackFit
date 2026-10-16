package it.quackfit.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Nell'applicazione completa, con il profilo test e la configurazione di default, le email vanno nel log. */
@SpringBootTest
@ActiveProfiles("test")
class MailContestoTest {

	@Autowired
	private MailService mailService;

	@Test
	void conLaConfigurazioneDiDefaultSiUsaIlLog() {
		assertThat(mailService).isInstanceOf(LogMailService.class);
	}

}
