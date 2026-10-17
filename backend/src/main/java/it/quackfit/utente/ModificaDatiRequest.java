package it.quackfit.utente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body di PATCH /api/me (vedi me.md, Modifica dati): stesse regole di nome e cognome della registrazione. */
public record ModificaDatiRequest(

		@NotBlank(message = "Il nome è obbligatorio")
		@Size(max = 50, message = "Massimo 50 caratteri")
		String nome,

		@NotBlank(message = "Il cognome è obbligatorio")
		@Size(max = 50, message = "Massimo 50 caratteri")
		String cognome) {

	/** Pulizia dell'input: strip() sui testi. */
	public ModificaDatiRequest {
		nome = nome == null ? null : nome.strip();
		cognome = cognome == null ? null : cognome.strip();
	}

}
