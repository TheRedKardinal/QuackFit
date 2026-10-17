package it.quackfit.config;

/**
 * Archivio esterno delle immagini (Cloudinary). È un'interfaccia, come MailService: il service della foto profilo
 * non dipende dal fornitore, e i test lo sostituiscono con una versione simulata.
 */
public interface ArchivioImmagini {

	/**
	 * Carica l'immagine con un id pubblico fisso, sostituendo quella che c'era con lo stesso id.
	 *
	 * @param contenuto  i byte dell'immagine, già controllati
	 * @param tipoMime   image/jpeg, image/png o image/webp
	 * @param idPubblico es. avatar/&lt;id utente&gt;
	 * @return l'URL https dell'immagine caricata
	 * @throws CaricamentoImmagineFallitoException se l'archivio non è configurato, non risponde o rifiuta il file
	 */
	String carica(byte[] contenuto, String tipoMime, String idPubblico);

}
