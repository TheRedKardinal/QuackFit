package it.quackfit.common;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * Gestione centralizzata degli errori: ogni eccezione diventa un {@link ErroreResponse}.
 * Mai stack trace o messaggi tecnici nella risposta.
 */
@Slf4j
@RestControllerAdvice
public class ExceptionsHandler {

	private final Clock clock;

	public ExceptionsHandler(Clock clock) {
		this.clock = clock;
	}

	/** Errori previsti dalle spec, lanciati dai service. */
	@ExceptionHandler(ApplicazioneException.class)
	public ResponseEntity<ErroreResponse> applicazione(ApplicazioneException e) {
		return risposta(ErroreResponse.da(e.getCodice(), e.getMessage(), adesso()));
	}

	/** Validazione dei record di richiesta (@Valid): un messaggio per ogni campo sbagliato. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErroreResponse> validazione(MethodArgumentNotValidException e) {
		Map<String, String> campi = new LinkedHashMap<>();
		for (FieldError errore : e.getBindingResult().getFieldErrors()) {
			// se un campo viola più regole, basta il primo messaggio
			campi.putIfAbsent(errore.getField(), errore.getDefaultMessage());
		}
		return risposta(ErroreResponse.da(
				CodiceErrore.VALIDAZIONE, "Uno o più campi non sono validi", campi, adesso()));
	}

	/** Body JSON malformato o con tipi sbagliati. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErroreResponse> bodyNonLeggibile(HttpMessageNotReadableException e) {
		return risposta(ErroreResponse.da(
				CodiceErrore.VALIDAZIONE, "Il corpo della richiesta non è valido", adesso()));
	}

	/** Parametro dell'URL del tipo sbagliato (es. un id che non è un UUID). */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErroreResponse> parametroNonValido(MethodArgumentTypeMismatchException e) {
		return risposta(ErroreResponse.da(
				CodiceErrore.VALIDAZIONE, "Uno o più campi non sono validi",
				Map.of(e.getName(), "Valore non valido"), adesso()));
	}

	/** Endpoint inesistente. */
	@ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
	public ResponseEntity<ErroreResponse> endpointInesistente(Exception e) {
		return risposta(ErroreResponse.da(CodiceErrore.NON_TROVATO, "Risorsa non trovata", adesso()));
	}

	/** Metodo HTTP non previsto per l'endpoint. */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErroreResponse> metodoNonConsentito(HttpRequestMethodNotSupportedException e) {
		return risposta(ErroreResponse.da(
				CodiceErrore.METODO_NON_CONSENTITO, "Metodo non consentito per questa risorsa", adesso()));
	}

	/** Qualunque altro errore: dettagli solo nel log, al client un messaggio generico. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErroreResponse> imprevisto(Exception e) {
		log.error("Errore imprevisto", e);
		return risposta(ErroreResponse.da(
				CodiceErrore.ERRORE_INTERNO, "Si è verificato un errore imprevisto, riprova più tardi", adesso()));
	}

	private Instant adesso() {
		return Instant.now(clock);
	}

	private ResponseEntity<ErroreResponse> risposta(ErroreResponse errore) {
		return ResponseEntity.status(errore.status()).body(errore);
	}

}
