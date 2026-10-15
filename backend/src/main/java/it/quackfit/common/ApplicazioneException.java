package it.quackfit.common;

/**
 * Eccezione lanciata dai service per un errore previsto dalle spec.
 * L'ExceptionsHandler la trasforma nella risposta con lo status del codice.
 */
public class ApplicazioneException extends RuntimeException {

	private final CodiceErrore codice;

	public ApplicazioneException(CodiceErrore codice, String messaggio) {
		super(messaggio);
		this.codice = codice;
	}

	public CodiceErrore getCodice() {
		return codice;
	}

}
