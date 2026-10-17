package it.quackfit.common;

import java.util.Map;

/**
 * Eccezione lanciata dai service per un errore previsto dalle spec.
 * L'ExceptionsHandler la trasforma nella risposta con lo status del codice.
 */
public class ApplicazioneException extends RuntimeException {

	private final CodiceErrore codice;
	private final Map<String, String> campi;

	public ApplicazioneException(CodiceErrore codice, String messaggio) {
		this(codice, messaggio, Map.of());
	}

	/**
	 * Errore legato a uno o più campi, deciso dal service e non da @Valid (es. "nuova password uguale all'attuale"):
	 * la risposta ha gli stessi `campi` di un errore di validazione, così il frontend lo mostra sotto il campo.
	 */
	public ApplicazioneException(CodiceErrore codice, String messaggio, Map<String, String> campi) {
		super(messaggio);
		this.codice = codice;
		this.campi = Map.copyOf(campi);
	}

	public CodiceErrore getCodice() {
		return codice;
	}

	public Map<String, String> getCampi() {
		return campi;
	}

}
