package it.quackfit.utente;

import java.time.Instant;
import java.util.UUID;

/** Un utente nella lista dell'admin (vedi admin.md, Lista). */
public record UtenteAdminResponse(UUID id, String nome, String cognome, String email, Ruolo ruolo,
		boolean emailVerificata, Instant creatoAt) {

	public static UtenteAdminResponse da(Utente utente) {
		return new UtenteAdminResponse(utente.getId(), utente.getNome(), utente.getCognome(), utente.getEmail(),
				utente.getRuolo(), utente.isEmailVerificata(), utente.getCreatedAt());
	}

}
