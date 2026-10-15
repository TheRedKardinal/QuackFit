package it.quackfit.common;

import java.time.Instant;
import java.util.Map;

/**
 * Formato unico di ogni errore restituito dalle API.
 * {@code campi} è valorizzato solo per gli errori di validazione, altrimenti è vuoto.
 */
public record ErroreResponse(
		int status,
		String codice,
		String errore,
		String messaggio,
		Map<String, String> campi,
		Instant timestamp) {

	public static ErroreResponse da(CodiceErrore codice, String messaggio, Instant timestamp) {
		return da(codice, messaggio, Map.of(), timestamp);
	}

	public static ErroreResponse da(CodiceErrore codice, String messaggio, Map<String, String> campi, Instant timestamp) {
		return new ErroreResponse(
				codice.getStatus().value(),
				codice.name(),
				codice.getStatus().getReasonPhrase(),
				messaggio,
				campi,
				timestamp);
	}

}
