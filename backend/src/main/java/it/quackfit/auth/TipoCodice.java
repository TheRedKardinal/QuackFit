package it.quackfit.auth;

/** Per quale operazione è stato generato un codice di verifica. */
public enum TipoCodice {
	VERIFICA_EMAIL,
	RESET_PASSWORD,
	CAMBIO_EMAIL
}
