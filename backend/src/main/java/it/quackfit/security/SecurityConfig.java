package it.quackfit.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import it.quackfit.utente.UtenteRepository;

/**
 * Configurazione di Spring Security: sessioni stateless con JWT e regole di accesso
 * (vedi backend/CLAUDE.md, sezione Sicurezza).
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
			UtenteRepository utenteRepository, GestoreErroriSicurezza gestoreErrori) {
		http
				// il token viaggia nell'header Authorization, non in un cookie: niente CSRF
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(sessione -> sessione.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// usa il CorsConfigurationSource definito in config/CorsConfig
				.cors(Customizer.withDefaults())
				.headers(header -> header
						.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
						.frameOptions(frame -> frame.deny()))
				// X-Content-Type-Options: nosniff è già attivo di default
				.authorizeHttpRequests(regole -> regole
						.requestMatchers("/api/auth/**").permitAll()
						.requestMatchers("/api/admin/**").access(RegoleAccesso.adminConEmailVerificata())
						// le uniche rotte ammesse con email non verificata
						.requestMatchers(HttpMethod.GET, "/api/me").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/me/verification", "/api/me/verification/resend")
						.authenticated()
						.anyRequest().access(RegoleAccesso.emailVerificata()))
				.exceptionHandling(errori -> errori
						.authenticationEntryPoint(gestoreErrori)
						.accessDeniedHandler(gestoreErrori))
				.addFilterBefore(new JwtFiltro(jwtService, utenteRepository), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
