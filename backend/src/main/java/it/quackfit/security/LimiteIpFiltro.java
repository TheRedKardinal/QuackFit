package it.quackfit.security;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import it.quackfit.common.CodiceErrore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limite di 20 richieste al minuto per IP su /api/auth/**. Oltre il limite risponde 429 TROPPE_RICHIESTE
 * e interrompe la catena: la richiesta non arriva al controller.
 *
 * L'IP è getRemoteAddr(): in prod la RemoteIpValve di Tomcat lo sostituisce già con quello letto
 * da X-Forwarded-For (server.forward-headers-strategy=native).
 *
 * Non è un bean (come JwtFiltro): lo crea SecurityConfig, altrimenti verrebbe eseguito due volte.
 */
public class LimiteIpFiltro extends OncePerRequestFilter {

	private static final String RADICE_AUTH = "/api/auth/";

	private final LimiteRichiesteIp limite;
	private final GestoreErroriSicurezza gestoreErrori;

	public LimiteIpFiltro(LimiteRichiesteIp limite, GestoreErroriSicurezza gestoreErrori) {
		this.limite = limite;
		this.gestoreErrori = gestoreErrori;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith(request.getContextPath() + RADICE_AUTH);
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
