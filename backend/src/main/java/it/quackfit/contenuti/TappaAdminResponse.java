package it.quackfit.contenuti;

import java.util.List;
import java.util.UUID;

/**
 * Tappa nella vista admin con le sue lezioni attive in ordine: la stessa forma nell'elenco e nelle risposte di
 * creazione (lezioni vuote) e modifica.
 */
public record TappaAdminResponse(UUID id, String titolo, String descrizione, int ordine,
		List<LezioneAdminResponse> lezioni) {

	public static TappaAdminResponse da(Tappa tappa, List<LezioneAdminResponse> lezioni) {
		return new TappaAdminResponse(tappa.getId(), tappa.getTitolo(), tappa.getDescrizione(), tappa.getOrdine(),
				lezioni);
	}

}
