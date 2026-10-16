package it.quackfit.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Nessuna email vera: il codice viene scritto nel log, per provare l'app in locale senza un account email.
 * È l'unico punto in cui il codice compare in chiaro. Con @Profile("!prod") non può esistere in produzione,
 * qualunque sia MAIL_MODALITA.
 */
@Slf4j
@Service
@Profile("!prod")
@ConditionalOnProperty(name = "quackfit.mail.modalita", havingValue = "log")
public class LogMailService extends MailServiceBase {

	public LogMailService(ComposizioneEmail composizione) {
		super(composizione);
	}

	@Override
	protected void consegna(Email email) {
		log.info("[EMAIL SOLO NEL LOG] a {} - \"{}\" - codice: {}", email.destinatario(), email.oggetto(), email.codice());
	}

}
