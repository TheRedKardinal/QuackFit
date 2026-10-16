package it.quackfit.tentativi;

import java.time.Instant;
import java.util.UUID;

/**
 * Riga di {@link ResocontoQuery#intestazione}: il tentativo e la sua lezione, anche se la lezione è stata cancellata
 * dopo il tentativo.
 *
 * @param finitoAt null se il tentativo è in corso
 */
public record IntestazioneResoconto(
		UUID tentativoId,
		EsitoTentativo esito,
		int errori,
		Instant iniziatoAt,
		Instant finitoAt,
		UUID lezioneId,
		String lezioneTitolo) {
}
