package it.quackfit.mail;

/**
 * Email pronta da consegnare. {@code codice} serve solo all'implementazione di log:
 * le altre lo spediscono dentro {@code html} e non devono mai registrarlo.
 */
public record Email(String destinatario, String oggetto, String html, String codice) {
}
