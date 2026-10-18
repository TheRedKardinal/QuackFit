package it.quackfit.utente;

/** Evento della cancellazione dell'account: la foto con questo id pubblico va eliminata da Cloudinary. */
public record FotoDaEliminare(String idPubblico) {
}
