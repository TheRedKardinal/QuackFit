package it.quackfit.tentativi;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Body di POST /api/attempts/{id}/answers (tentativi.md, Risposta): una risposta alla volta.
 * `opzioneId` per VERO_FALSO e SCELTA_MULTIPLA, `coppie` per ABBINAMENTO; quale dei due serve dipende dalla domanda,
 * quindi lo controlla il service (l'altro deve essere assente).
 */
public record RispostaRequest(

		@NotNull(message = "La domanda è obbligatoria")
		UUID domandaId,

		UUID opzioneId,

		List<@NotNull(message = "La coppia è obbligatoria") @Valid CoppiaRequest> coppie) {

	/** Una coppia scelta dall'utente: un elemento di sinistra abbinato a uno di destra. */
	public record CoppiaRequest(

			@NotNull(message = "L'elemento di sinistra è obbligatorio")
			UUID sinistroId,

			@NotNull(message = "L'elemento di destra è obbligatorio")
			UUID destroId) {
	}

}
