package it.quackfit.contenuti;

import java.util.UUID;

/**
 * Lezione nella vista admin (vedi admin.md, Tappe e Lezioni): la stessa forma nell'elenco e nelle risposte di
 * creazione (numeroDomande = 0) e modifica.
 */
public record LezioneAdminResponse(UUID id, String titolo, String descrizione, int ordine, UUID tappaId,
		long numeroDomande) {

	public static LezioneAdminResponse da(Lezione lezione, long numeroDomande) {
		return new LezioneAdminResponse(lezione.getId(), lezione.getTitolo(), lezione.getDescrizione(),
				lezione.getOrdine(), lezione.getTappa().getId(), numeroDomande);
	}

}
