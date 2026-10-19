package it.quackfit.tentativi;

import java.util.UUID;

/** Risposta di POST /api/attempts: l'id del nuovo tentativo. */
public record AvvioTentativoResponse(UUID tentativoId) {
}
