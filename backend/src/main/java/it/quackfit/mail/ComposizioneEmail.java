package it.quackfit.mail;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Compone le email dai template Thymeleaf in templates/mail/.
 * I template usano solo th:text, che fa l'escape: il nome scritto dall'utente non diventa HTML.
 */
@Component
public class ComposizioneEmail {

	static final String OGGETTO_VERIFICA = "Il tuo codice di verifica QuackFit";
	static final String OGGETTO_RESET = "Reimposta la password di QuackFit";
	static final String OGGETTO_CAMBIO_EMAIL = "Conferma il nuovo indirizzo email";

	private final ITemplateEngine templateEngine;

	public ComposizioneEmail(ITemplateEngine templateEngine) {
		this.templateEngine = templateEngine;
	}

	public Email verificaEmail(String destinatario, String nome, String codice) {
		return componi("mail/verifica-email", OGGETTO_VERIFICA, destinatario, nome, codice);
	}

	public Email resetPassword(String destinatario, String nome, String codice) {
		return componi("mail/reset-password", OGGETTO_RESET, destinatario, nome, codice);
	}

	public Email cambioEmail(String destinatario, String nome, String codice) {
		return componi("mail/cambio-email", OGGETTO_CAMBIO_EMAIL, destinatario, nome, codice);
	}

	private Email componi(String template, String oggetto, String destinatario, String nome, String codice) {
		Context contesto = new Context();
		contesto.setVariable("oggetto", oggetto);
		contesto.setVariable("nome", nome);
		contesto.setVariable("codice", codice);
		return new Email(destinatario, oggetto, templateEngine.process(template, contesto), codice);
	}

}
