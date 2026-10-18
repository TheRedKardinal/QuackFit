package it.quackfit.contenuti;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body di POST /api/admin/stages/{stageId}/lessons: l'ordine non si sceglie, la lezione va in fondo alla tappa
 * (vedi admin.md, Ordine).
 */
public record CreazioneLezioneRequest(

		@NotBlank(message = "Il titolo è obbligatorio")
		@Size(max = Lezione.MAX_TITOLO, message = "Massimo " + Lezione.MAX_TITOLO + " caratteri")
		String titolo,

		@NotBlank(message = "La descrizione è obbligatoria")
		@Size(max = Lezione.MAX_DESCRIZIONE, message = "Massimo " + Lezione.MAX_DESCRIZIONE + " caratteri")
		String descrizione) {

	/** Pulizia dell'input: strip() sui testi. */
	public CreazioneLezioneRequest {
		titolo = titolo == null ? null : titolo.strip();
		descrizione = descrizione == null ? null : descrizione.strip();
	}

}
