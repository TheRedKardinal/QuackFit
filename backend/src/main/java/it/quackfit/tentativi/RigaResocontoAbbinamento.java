package it.quackfit.tentativi;

import java.util.UUID;

import it.quackfit.contenuti.TipoDomanda;

/**
 * Riga di {@link ResocontoQuery#righeAbbinamenti}: una coppia della prima risposta a una domanda ABBINAMENTO, con il
 * destro giusto, anche se la domanda è stata cancellata dopo il tentativo. Le righe di una domanda si raggruppano
 * per domandaId.
 *
 * @param destroId    il destro scelto con la prima risposta
 * @param soluzioneId il destro giusto per questo sinistro
 */
public record RigaResocontoAbbinamento(
		UUID domandaId,
		int ordine,
		String testo,
		TipoDomanda tipo,
		String spiegazione,
		UUID sinistroId,
		String sinistroTesto,
		UUID destroId,
		String destroTesto,
		UUID soluzioneId,
		String soluzioneTesto) {
}
