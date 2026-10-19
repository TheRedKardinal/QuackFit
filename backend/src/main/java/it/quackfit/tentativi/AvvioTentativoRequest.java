package it.quackfit.tentativi;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Body di POST /api/attempts (tentativi.md, Avvio). */
public record AvvioTentativoRequest(

		@NotNull(message = "La lezione è obbligatoria")
		UUID lezioneId) {
}
