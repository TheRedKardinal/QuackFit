package it.quackfit.contenuti;

import java.util.UUID;

/** Una lezione nella mappa (percorso.md, Mappa): niente domande né il loro numero. */
public record LezionePercorsoResponse(UUID id, String titolo, String descrizione, int ordine, StatoLezione stato) {

	public static LezionePercorsoResponse da(Lezione lezione, StatoLezione stato) {
		return new LezionePercorsoResponse(lezione.getId(), lezione.getTitolo(), lezione.getDescrizione(),
				lezione.getOrdine(), stato);
	}

}
