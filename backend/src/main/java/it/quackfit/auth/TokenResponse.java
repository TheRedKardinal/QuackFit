package it.quackfit.auth;

/** Risposta di registrazione e login: il solo token JWT. */
public record TokenResponse(String token) {
}
