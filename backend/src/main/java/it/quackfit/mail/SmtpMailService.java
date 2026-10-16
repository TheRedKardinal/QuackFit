package it.quackfit.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Invio tramite SMTP di Gmail, solo per provare email vere in locale (MAIL_MODALITA=smtp).
 * Host, account e timeout sono le chiavi spring.mail.* di application.properties.
 */
@Service
@ConditionalOnProperty(name = "quackfit.mail.modalita", havingValue = "smtp")
public class SmtpMailService extends MailServiceBase {

	private final JavaMailSender mailSender;
	private final String mittente;
	private final String nomeMittente;

	public SmtpMailService(ComposizioneEmail composizione, JavaMailSender mailSender,
			@Value("${quackfit.mail.mittente}") String mittente,
			@Value("${quackfit.mail.nome-mittente}") String nomeMittente) {
		super(composizione);
		if (mittente.isBlank()) {
			throw new IllegalStateException("MAIL_MITTENTE mancante: serve con MAIL_MODALITA=smtp");
		}
		this.mailSender = mailSender;
		this.mittente = mittente;
		this.nomeMittente = nomeMittente;
	}

	@Override
	protected void consegna(Email email) {
		try {
			MimeMessage messaggio = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(messaggio, StandardCharsets.UTF_8.name());
			helper.setFrom(mittente, nomeMittente);
			helper.setTo(email.destinatario());
			helper.setSubject(email.oggetto());
			helper.setText(email.html(), true);
			mailSender.send(messaggio);
		} catch (MessagingException | UnsupportedEncodingException e) {
			throw new InvioEmailFallitoException("messaggio SMTP non valido (" + e.getClass().getSimpleName() + ")");
		}
	}

}
