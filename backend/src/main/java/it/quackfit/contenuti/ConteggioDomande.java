package it.quackfit.contenuti;

import java.util.UUID;

/** Proiezione di DomandaRepository.contaPerLezione: domande attive di una lezione. */
public record ConteggioDomande(UUID lezioneId, long numero) {
}
