package it.quackfit.auth;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;

/**
 * Body di POST /api/auth/password/forgot. Nessun controllo di formato sull'email: un'email strana
 * non è registrata e riceve la stessa risposta di tutte le altre (vedi auth.md, Password dimenticata).
 */
public record PasswordDimenticataRequest(

		@NotBlank(message = "L'email è obbligatoria")
		String email) {

	/** Pulizia dell'input: email con strip() e in minuscolo. */
	public PasswordDimenticataRequest {
		email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

}
