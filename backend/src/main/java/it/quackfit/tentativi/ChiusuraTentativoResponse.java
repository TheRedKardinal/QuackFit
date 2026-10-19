package it.quackfit.tentativi;

import it.quackfit.utente.Streak;

/** Esito della chiusura (tentativi.md, Chiusura): esito, errori e la streak appena aggiornata. */
public record ChiusuraTentativoResponse(EsitoTentativo esito, int errori, StreakResponse streak) {

	public record StreakResponse(int valore, int record) {
	}

	public static ChiusuraTentativoResponse da(TentativoLezione tentativo, Streak streak) {
		return new ChiusuraTentativoResponse(tentativo.getEsito(), tentativo.getErrori(),
				new StreakResponse(streak.getValore(), streak.getRecord()));
	}

}
