package it.quackfit.auth;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;

/**
 * Body di POST /api/auth/login. Nessun controllo di formato sull'email: un'email strana dà semplicemente 401.
 * I campi vuoti invece danno 400 VALIDAZIONE (vedi auth.md, Login).
 */
public record LoginRequest(

		@NotBlank(message = "L'email è obbligatoria")
		String email,

		@NotBlank(message = "La password è obbligatoria")
		String password) {

	/** Pulizia dell'input: email con strip() e in minuscolo. La password non si tocca. */
	public LoginRequest {
		email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

}
