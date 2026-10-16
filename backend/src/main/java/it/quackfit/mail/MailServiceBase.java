package it.quackfit.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * Parte comune delle implementazioni: compone l'email e la consegna senza mai lanciare eccezioni.
 * Ogni implementazione scrive solo {@link #consegna(Email)}.
 */
@Slf4j
public abstract class MailServiceBase implements MailService {

	private final ComposizioneEmail composizione;

	protected MailServiceBase(ComposizioneEmail composizione) {
		this.composizione = composizione;
	}

	/** Consegna l'email; in caso di problemi lancia un'eccezione, che qui viene catturata. */
	protected abstract void consegna(Email email);

	@Override
	public void inviaVerificaEmail(String destinatario, String nome, String codice) {
		inviaSenzaEccezioni(composizione.verificaEmail(destinatario, nome, codice));
	}

	@Override
	public void inviaResetPassword(String destinatario, String nome, String codice) {
		inviaSenzaEccezioni(composizione.resetPassword(destinatario, nome, codice));
	}

	@Override
	public void inviaConfermaCambioEmail(String destinatario, String nome, String codice) {
		inviaSenzaEccezioni(composizione.cambioEmail(destinatario, nome, codice));
	}

	private void inviaSenzaEccezioni(Email email) {
		try {
			consegna(email);
		} catch (InvioEmailFallitoException e) {
			// messaggio scritto da noi: contiene solo dati sicuri (es. lo stato HTTP)
			log.error("Invio dell'email a {} non riuscito: {}", mascheraEmail(email.destinatario()), e.getMessage());
		} catch (RuntimeException e) {
			// eccezione di terzi (es. SMTP): il messaggio potrebbe contenere l'indirizzo, si registra solo il tipo
			log.error("Invio dell'email a {} non riuscito: {}", mascheraEmail(email.destinatario()),
					e.getClass().getSimpleName());
		}
	}

	/** "mario.rossi@example.com" → "m***@example.com". */
	static String mascheraEmail(String email) {
		int chiocciola = email == null ? -1 : email.indexOf('@');
		if (chiocciola < 1) {
			return "***";
		}
		return email.charAt(0) + "***" + email.substring(chiocciola);
	}

}
