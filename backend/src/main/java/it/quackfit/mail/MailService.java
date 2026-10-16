package it.quackfit.mail;

/**
 * Invio delle email con i codici. Un metodo per ogni email.
 *
 * Nessun metodo lancia eccezioni: se la consegna fallisce l'errore finisce nel log e il chiamante
 * prosegue (vedi auth.md: la registrazione resta valida anche se l'invio fallisce).
 */
public interface MailService {

	void inviaVerificaEmail(String destinatario, String nome, String codice);

	void inviaResetPassword(String destinatario, String nome, String codice);

	/** Il destinatario è il nuovo indirizzo, quello da confermare. */
	void inviaConfermaCambioEmail(String destinatario, String nome, String codice);

}
