package it.quackfit.auth;

import java.util.Locale;

import it.quackfit.common.PasswordValida;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body di POST /api/auth/register (vedi auth.md, Registrazione). */
public record RegistrazioneRequest(

		@NotBlank(message = "Il nome è obbligatorio")
		@Size(max = 50, message = "Massimo 50 caratteri")
		String nome,

		@NotBlank(message = "Il cognome è obbligatorio")
		@Size(max = 50, message = "Massimo 50 caratteri")
		String cognome,

		// @Email da solo accetta anche "mario@localhost": la regexp chiede un punto nel dominio.
		// Il gruppo è facoltativo, (...)?, perché l'email vuota la segnala solo @NotBlank (un messaggio per campo)
		@NotBlank(message = "L'email è obbligatoria")
		@Email(regexp = "(.+@.+\\..+)?", message = "Formato email non valido")
		@Size(max = 254, message = "Massimo 254 caratteri")
		String email,

		@NotBlank(message = "La password è obbligatoria")
		@PasswordValida
		String password) {

	/** Pulizia dell'input: strip() sui testi, email in minuscolo. La password non si tocca. */
	public RegistrazioneRequest {
		nome = nome == null ? null : nome.strip();
		cognome = cognome == null ? null : cognome.strip();
		email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

}
