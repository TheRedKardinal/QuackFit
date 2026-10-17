package it.quackfit.config;

/**
 * Caricamento su Cloudinary non riuscito. Il messaggio va nel log: contiene solo lo stato HTTP o il tipo
 * dell'errore, o il nome delle chiavi mancanti, mai i loro valori né il contenuto del file.
 */
public class CaricamentoImmagineFallitoException extends RuntimeException {

	public CaricamentoImmagineFallitoException(String messaggio) {
		super(messaggio);
	}

}
