package it.quackfit.auth;

/**
 * Esito di una verifica riuscita. Al chiamante torna solo questo, mai l'entità CodiceVerifica:
 * così non può riscriverla con un tentativiFalliti vecchio.
 *
 * @param nuovaEmail l'indirizzo da confermare (solo per CAMBIO_EMAIL, altrimenti null)
 */
public record CodiceVerificato(String nuovaEmail) {
}
