package it.quackfit.utente;

import jakarta.validation.constraints.NotBlank;

/**
 * Body di POST /api/me/email/confirm. Nessun controllo di formato: un valore che non è un codice
 * a 6 cifre è semplicemente un codice sbagliato e conta come tentativo (vedi auth.md).
 */
public record ConfermaCambioEmailRequest(

		@NotBlank(message = "Il codice è obbligatorio")
		String codice) {

	/** Pulizia dell'input: strip() sul codice. */
	public ConfermaCambioEmailRequest {
		codice = codice == null ? null : codice.strip();
	}

}
