package it.quackfit.security;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import it.quackfit.security.JwtService.DatiToken;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Legge il token dall'header Authorization e, se è valido, autentica la richiesta
 * con l'Utente caricato dal database come principal.
 *
 * Un token non valido NON blocca qui la richiesta: semplicemente non autentica.
 * Così le rotte pubbliche (/api/auth/**) funzionano anche con un vecchio token nel browser,
 * mentre sulle rotte protette ci pensa l'entry point a rispondere 401 NON_AUTENTICATO.
 *
 * Non è un bean: lo crea SecurityConfig, altrimenti Spring Boot lo registrerebbe
 * anche come filtro servlet e verrebbe eseguito due volte.
 */
public class JwtFiltro extends OncePerRequestFilter {

	private static final String PREFISSO = "Bearer ";

	private final JwtService jwtService;
	private final UtenteRepository utenteRepository;

	public JwtFiltro(JwtService jwtService, UtenteRepository utenteRepository) {
		this.jwtService = jwtService;
		this.utenteRepository = utenteRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(PREFISSO)) {
			utenteValido(header.substring(PREFISSO.length())).ifPresent(utente -> {
				var autenticazione = new UsernamePasswordAuthenticationToken(
						utente, null, List.of(new SimpleGrantedAuthority("ROLE_" + utente.getRuolo().name())));
				SecurityContextHolder.getContext().setAuthentication(autenticazione);
			});
		}
		chain.doFilter(request, response);
	}

	/** L'utente del token, solo se esiste, è attivo e il token non è precedente a tokenValidiDal. */
	private Optional<Utente> utenteValido(String token) {
		Optional<DatiToken> dati = jwtService.leggi(token);
		if (dati.isEmpty()) {
			return Optional.empty();
		}
		return utenteRepository.findById(dati.get().utenteId())
				.filter(utente -> utente.getDeletedAt() == null)
				.filter(utente -> emessoDopoTokenValidiDal(dati.get(), utente));
	}

	/**
	 * iat ha precisione al secondo: si confronta con tokenValidiDal troncato al secondo,
	 * così il token emesso nello stesso secondo (es. alla registrazione) resta valido (vedi auth.md, Note).
	 */
	private boolean emessoDopoTokenValidiDal(DatiToken dati, Utente utente) {
		return !dati.emessoIl().isBefore(utente.getTokenValidiDal().truncatedTo(ChronoUnit.SECONDS));
	}

}
