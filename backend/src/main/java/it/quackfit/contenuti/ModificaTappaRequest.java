package it.quackfit.contenuti;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body di PATCH /api/admin/stages/{stageId}: ogni campo è facoltativo, assente o null = invariato. Un testo presente
 * non può essere vuoto (dopo strip() resta "", che @Pattern rifiuta; null invece passa). Il limite superiore di
 * `ordine` (il numero di tappe) lo controlla il service.
 */
public record ModificaTappaRequest(

		@Pattern(regexp = "(?s).+", message = "Il titolo non può essere vuoto")
		@Size(max = Tappa.MAX_TITOLO, message = "Massimo " + Tappa.MAX_TITOLO + " caratteri")
		String titolo,

		@Pattern(regexp = "(?s).+", message = "La descrizione non può essere vuota")
		@Size(max = Tappa.MAX_DESCRIZIONE, message = "Massimo " + Tappa.MAX_DESCRIZIONE + " caratteri")
		String descrizione,

		@Min(value = 1, message = "La posizione deve essere almeno 1")
		Integer ordine) {

	/** Pulizia dell'input: strip() sui testi. */
	public ModificaTappaRequest {
		titolo = titolo == null ? null : titolo.strip();
		descrizione = descrizione == null ? null : descrizione.strip();
	}

}
