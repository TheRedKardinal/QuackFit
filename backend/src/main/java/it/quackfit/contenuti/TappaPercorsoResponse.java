package it.quackfit.contenuti;

import java.util.List;
import java.util.UUID;

/** Una tappa nella mappa (percorso.md, Mappa), con le sue lezioni attive in ordine e il loro stato. */
public record TappaPercorsoResponse(UUID id, String titolo, String descrizione, int ordine,
		List<LezionePercorsoResponse> lezioni) {

	public static TappaPercorsoResponse da(Tappa tappa, List<LezionePercorsoResponse> lezioni) {
		return new TappaPercorsoResponse(tappa.getId(), tappa.getTitolo(), tappa.getDescrizione(), tappa.getOrdine(),
				lezioni);
	}

}
