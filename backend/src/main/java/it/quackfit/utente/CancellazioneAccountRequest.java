package it.quackfit.utente;

import jakarta.validation.constraints.NotBlank;

/** Body di DELETE /api/me: la password attuale, senza pulizia né regole di formato (vedi me.md, Cancellazione account). */
public record CancellazioneAccountRequest(

		@NotBlank(message = "La password è obbligatoria")
		String password) {
}
