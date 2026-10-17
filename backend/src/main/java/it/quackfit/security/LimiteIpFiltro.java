package it.quackfit.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.web.filter.OncePerRequestFilter;

import it.quackfit.common.CodiceErrore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limite di 20 richieste al minuto per IP su /api/auth/** e sugli endpoint che chiedono la password attuale
 * (PATCH /api/me/password, POST /api/me/email, DELETE /api/me), con un solo contatore per IP.
 * Oltre il limite risponde 429 TROPPE_RICHIESTE e interrompe la catena: la richiesta non arriva al controller.
 * Gli altri endpoint di /api/me (es. GET /api/me, chiamato a ogni avvio dell'app) non consumano il limite.
 *
 * L'IP è getRemoteAddr(): in prod la RemoteIpValve di Tomcat lo sostituisce già con quello letto
 * da X-Forwarded-For (server.forward-headers-strategy=native).
 *
 * Non è un bean (come JwtFiltro): lo crea SecurityConfig, altrimenti verrebbe eseguito due volte.
 */
public class LimiteIpFiltro extends OncePerRequestFilter {

	private static final String RADICE_AUTH = "/api/auth/";

	/** Metodo e percorso esatti: senza un limite, con un token rubato si potrebbe provare la password a raffica. */
	private static final Set<String> CON_PASSWORD_ATTUALE = Set.of(
			"PATCH /api/me/password",
			"POST /api/me/email",
			"DELETE /api/me");

	private final LimiteRichiesteIp limite;
	private final GestoreErroriSicurezza gestoreErrori;

	public LimiteIpFiltro(LimiteRichiesteIp limite, GestoreErroriSicurezza gestoreErrori) {
		this.limite = limite;
		this.gestoreErrori = gestoreErrori;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String percorso = request.getRequestURI().substring(request.getContextPath().length());
		boolean limitato = percorso.startsWith(RADICE_AUTH)
				|| CON_PASSWORD_ATTUALE.contains(request.getMethod() + " " + percorso);
		return !limitato;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!limite.consenti(request.getRemoteAddr())) {
			gestoreErrori.scrivi(response, CodiceErrore.TROPPE_RICHIESTE);
			return;
		}
		chain.doFilter(request, response);
	}

}
