package it.quackfit.contenuti;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body di POST /api/admin/stages: l'ordine non si sceglie, la tappa va in fondo (vedi admin.md, Ordine). */
public record CreazioneTappaRequest(

		@NotBlank(message = "Il titolo è obbligatorio")
		@Size(max = Tappa.MAX_TITOLO, message = "Massimo " + Tappa.MAX_TITOLO + " caratteri")
		String titolo,

		@NotBlank(message = "La descrizione è obbligatoria")
		@Size(max = Tappa.MAX_DESCRIZIONE, message = "Massimo " + Tappa.MAX_DESCRIZIONE + " caratteri")
		String descrizione) {

	/** Pulizia dell'input: strip() sui testi. */
	public CreazioneTappaRequest {
		titolo = titolo == null ? null : titolo.strip();
		descrizione = descrizione == null ? null : descrizione.strip();
	}

}
