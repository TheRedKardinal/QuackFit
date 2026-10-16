package it.quackfit.tentativi;

import java.util.UUID;

import it.quackfit.contenuti.TipoDomanda;

/**
 * Riga di {@link ResocontoQuery#righeOpzioni}: una domanda VERO_FALSO o SCELTA_MULTIPLA già risposta, con la prima
 * risposta data e la soluzione, anche se cancellata dopo il tentativo.
 *
 * @param opzioneId  l'opzione scelta con la prima risposta
 * @param corretta   la prima risposta era giusta?
 * @param soluzioneId l'opzione giusta
 */
public record RigaResocontoOpzione(
		UUID domandaId,
		int ordine,
		String testo,
		TipoDomanda tipo,
		String spiegazione,
		UUID opzioneId,
		String opzioneTesto,
		boolean corretta,
		UUID soluzioneId,
		String soluzioneTesto) {
}
