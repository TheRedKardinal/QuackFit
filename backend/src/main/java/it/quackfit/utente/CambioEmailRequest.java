package it.quackfit.utente;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body di POST /api/me/email (vedi me.md, Cambio email). La nuova email ha le stesse regole della registrazione;
 * la password è quella attuale, senza regole di formato (sbagliata → PASSWORD_ATTUALE_ERRATA dal service).
 */
public record CambioEmailRequest(

		// @Email da solo accetta anche "mario@localhost": la regexp chiede un punto nel dominio (come in registrazione)
		@NotBlank(message = "L'email è obbligatoria")
		@Email(regexp = "(.+@.+\\..+)?", message = "Formato email non valido")
		@Size(max = 254, message = "Massimo 254 caratteri")
		String nuovaEmail,

		@NotBlank(message = "La password è obbligatoria")
		String password) {

	/** Pulizia dell'input: email con strip() e in minuscolo. La password non si tocca. */
	public CambioEmailRequest {
		nuovaEmail = nuovaEmail == null ? null : nuovaEmail.strip().toLowerCase(Locale.ROOT);
	}

}
