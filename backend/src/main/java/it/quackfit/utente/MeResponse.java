package it.quackfit.utente;

/**
 * Risposta di GET /api/me (vedi me.md, Dati del profilo). {@code emailVerificata} serve al frontend per decidere
 * se mostrare la schermata del codice, non va mostrato nel profilo.
 */
public record MeResponse(String nome, String cognome, String email, Ruolo ruolo, String fotoProfilo,
		boolean emailVerificata, StreakResponse streak, int lezioniCompletate) {

	/** {@code streakAttuale} è il valore già calcolato in lettura, non quello salvato. */
	public static MeResponse da(Utente utente, int streakAttuale, int record, int lezioniCompletate) {
		return new MeResponse(utente.getNome(), utente.getCognome(), utente.getEmail(), utente.getRuolo(),
				utente.getFotoProfilo(), utente.isEmailVerificata(), new StreakResponse(streakAttuale, record),
				lezioniCompletate);
	}

}
