package it.quackfit.mail;

/**
 * Consegna non riuscita, lanciata dalle implementazioni e catturata da MailServiceBase.
 * Il messaggio finisce nel log: non deve mai contenere il codice, l'indirizzo o la chiave API.
 */
public class InvioEmailFallitoException extends RuntimeException {

	public InvioEmailFallitoException(String messaggio) {
		super(messaggio);
	}

}
