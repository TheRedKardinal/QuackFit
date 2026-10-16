package it.quackfit.auth;

/** Risposta con un solo messaggio per l'utente (es. password dimenticata). Stessa chiave degli errori. */
public record MessaggioResponse(String messaggio) {
}
