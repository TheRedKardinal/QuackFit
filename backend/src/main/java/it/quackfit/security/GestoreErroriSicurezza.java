package it.quackfit.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import it.quackfit.common.CodiceErrore;
import it.quackfit.common.ErroreResponse;
import it.quackfit.security.RegoleAccesso.AccessoNegato;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Risposte 401 e 403 di Spring Security nel formato unico degli errori.
 * Servono qui perché questi errori nascono nei filtri, prima dei controller,
 * quindi l'ExceptionsHandler non li vede.
 */
@Component
public class GestoreErroriSicurezza implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectMapper objectMapper;
	private final Clock clock;

	public GestoreErroriSicurezza(ObjectMapper objectMapper, Clock clock) {
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	/** Richiesta senza token valido su una rotta protetta. */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
			throws IOException {
		scrivi(response, CodiceErrore.NON_AUTENTICATO);
	}

	/** Utente autenticato ma senza accesso: il codice arriva dalla decisione di RegoleAccesso. */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
			throws IOException {
		CodiceErrore codice = CodiceErrore.ACCESSO_NEGATO;
		if (e instanceof AuthorizationDeniedException negata
				&& negata.getAuthorizationResult() instanceof AccessoNegato accessoNegato) {
			codice = accessoNegato.codice();
		}
		scrivi(response, codice);
	}

	private void scrivi(HttpServletResponse response, CodiceErrore codice) throws IOException {
		ErroreResponse errore = ErroreResponse.da(codice, messaggio(codice), Instant.now(clock));
		response.setStatus(errore.status());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), errore);
	}

	private static String messaggio(CodiceErrore codice) {
		return switch (codice) {
			case NON_AUTENTICATO -> "Accesso richiesto: effettua il login";
			case EMAIL_NON_VERIFICATA -> "Verifica la tua email per continuare";
			default -> "Non hai i permessi per questa operazione";
		};
	}

}
