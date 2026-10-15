package it.quackfit.common;

import org.springframework.http.HttpStatus;

/**
 * Codici di errore stabili restituiti dalle API, ognuno con il suo status HTTP.
 * Il frontend decide in base al codice, mai al testo del messaggio.
 */
public enum CodiceErrore {

	VALIDAZIONE(HttpStatus.BAD_REQUEST),
	CODICE_NON_VALIDO(HttpStatus.BAD_REQUEST),
	CODICE_SCADUTO(HttpStatus.BAD_REQUEST),
	TROPPI_TENTATIVI(HttpStatus.BAD_REQUEST),
	PASSWORD_ATTUALE_ERRATA(HttpStatus.BAD_REQUEST),
	FILE_NON_VALIDO(HttpStatus.BAD_REQUEST),

	NON_AUTENTICATO(HttpStatus.UNAUTHORIZED),
	CREDENZIALI_NON_VALIDE(HttpStatus.UNAUTHORIZED),

	ACCESSO_NEGATO(HttpStatus.FORBIDDEN),
	EMAIL_NON_VERIFICATA(HttpStatus.FORBIDDEN),
	LEZIONE_BLOCCATA(HttpStatus.FORBIDDEN),

	NON_TROVATO(HttpStatus.NOT_FOUND),

	METODO_NON_CONSENTITO(HttpStatus.METHOD_NOT_ALLOWED),

	EMAIL_GIA_USATA(HttpStatus.CONFLICT),
	TENTATIVO_CHIUSO(HttpStatus.CONFLICT),

	ATTENDI_REINVIO(HttpStatus.TOO_MANY_REQUESTS),
	TROPPE_RICHIESTE(HttpStatus.TOO_MANY_REQUESTS),

	ERRORE_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	CodiceErrore(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

}
