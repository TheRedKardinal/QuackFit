package it.quackfit.contenuti;

import java.util.List;
import java.util.UUID;

/** GET /api/admin/lessons/{lessonId}: la lezione con tutte le sue domande attive in ordine, complete di soluzioni. */
public record LezioneDettaglioAdminResponse(UUID id, String titolo, String descrizione, int ordine, UUID tappaId,
		List<DomandaAdminResponse> domande) {

	public static LezioneDettaglioAdminResponse da(Lezione lezione, List<DomandaAdminResponse> domande) {
		return new LezioneDettaglioAdminResponse(lezione.getId(), lezione.getTitolo(), lezione.getDescrizione(),
				lezione.getOrdine(), lezione.getTappa().getId(), domande);
	}

}
