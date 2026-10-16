package it.quackfit.security;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import it.quackfit.common.CodiceErrore;
import it.quackfit.utente.Ruolo;
import it.quackfit.utente.Utente;

/**
 * Regole di accesso che, quando negano, dicono anche il perché.
 *
 * Una decisione negativa è un {@link AccessoNegato} con il codice di errore: Spring Security la mette
 * dentro l'AuthorizationDeniedException, e GestoreErroriSicurezza la legge per scegliere
 * tra 403 ACCESSO_NEGATO e 403 EMAIL_NON_VERIFICATA. Le regole stanno solo qui.
 */
public final class RegoleAccesso {

	private RegoleAccesso() {
	}

	/** Decisione negativa con il codice da restituire al client. */
	public record AccessoNegato(CodiceErrore codice) implements AuthorizationResult {
		@Override
		public boolean isGranted() {
			return false;
		}
	}

	private static final AuthorizationResult CONSENTITO = new AuthorizationDecision(true);

	/** Utente autenticato con email verificata. */
	public static AuthorizationManager<RequestAuthorizationContext> emailVerificata() {
		return (autenticazione, contesto) -> {
			Utente utente = utente(autenticazione);
			if (utente == null) {
				return new AccessoNegato(CodiceErrore.NON_AUTENTICATO);
			}
			if (!utente.isEmailVerificata()) {
				return new AccessoNegato(CodiceErrore.EMAIL_NON_VERIFICATA);
			}
			return CONSENTITO;
		};
	}

	/**
	 * ADMIN o SUPERADMIN con email verificata. Prima il ruolo, poi l'email:
	 * un non admin riceve sempre ACCESSO_NEGATO, anche se non è verificato (vedi admin.md).
	 */
	public static AuthorizationManager<RequestAuthorizationContext> adminConEmailVerificata() {
		return (autenticazione, contesto) -> {
			Utente utente = utente(autenticazione);
			if (utente == null) {
				return new AccessoNegato(CodiceErrore.NON_AUTENTICATO);
			}
			if (utente.getRuolo() != Ruolo.ADMIN && utente.getRuolo() != Ruolo.SUPERADMIN) {
				return new AccessoNegato(CodiceErrore.ACCESSO_NEGATO);
			}
			if (!utente.isEmailVerificata()) {
				return new AccessoNegato(CodiceErrore.EMAIL_NON_VERIFICATA);
			}
			return CONSENTITO;
		};
	}

	/** L'Utente messo dal JwtFiltro, oppure null se la richiesta non è autenticata. */
	private static Utente utente(Supplier<? extends Authentication> autenticazione) {
		Authentication a = autenticazione.get();
		if (a != null && a.getPrincipal() instanceof Utente utente) {
			return utente;
		}
		return null;
	}

}
