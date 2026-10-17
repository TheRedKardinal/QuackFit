package it.quackfit.utente;

import it.quackfit.common.PasswordValida;
import jakarta.validation.constraints.NotBlank;

/**
 * Body di PATCH /api/me/password (vedi me.md, Cambio password). Le password non si puliscono.
 * La password attuale non ha regole di formato: una password sbagliata (anche oltre i 72 byte) dà
 * PASSWORD_ATTUALE_ERRATA dal service; la nuova si valida come in registrazione.
 */
public record CambioPasswordRequest(

		@NotBlank(message = "La password attuale è obbligatoria")
		String passwordAttuale,

		@NotBlank(message = "La nuova password è obbligatoria")
		@PasswordValida
		String nuovaPassword) {
}
