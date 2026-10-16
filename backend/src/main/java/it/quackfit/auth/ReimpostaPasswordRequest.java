package it.quackfit.auth;

import java.util.Locale;

import it.quackfit.common.PasswordValida;
import jakarta.validation.constraints.NotBlank;

/**
 * Body di POST /api/auth/password/reset (vedi auth.md, Reimpostazione password).
 * Nessun controllo di formato su email e codice: un valore strano dà semplicemente CODICE_NON_VALIDO.
 * La nuova password si valida come in registrazione.
 */
public record ReimpostaPasswordRequest(

		@NotBlank(message = "L'email è obbligatoria")
		String email,

		@NotBlank(message = "Il codice è obbligatorio")
		String codice,

		@NotBlank(message = "La nuova password è obbligatoria")
		@PasswordValida
		String nuovaPassword) {

	/** Pulizia dell'input: email con strip() e in minuscolo, codice con strip(). La password non si tocca. */
	public ReimpostaPasswordRequest {
		email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
		codice = codice == null ? null : codice.strip();
	}

}
