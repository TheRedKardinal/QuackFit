package it.quackfit.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS: solo l'origine del frontend (da configurazione), mai "*".
 * Niente credenziali: il token viaggia nell'header Authorization, non nei cookie.
 */
@Configuration
public class CorsConfig {

	@Bean
	public CorsConfigurationSource corsConfigurationSource(@Value("${quackfit.frontend-url}") String frontendUrl) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(List.of(frontendUrl));
		cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));

		UrlBasedCorsConfigurationSource sorgente = new UrlBasedCorsConfigurationSource();
		sorgente.registerCorsConfiguration("/api/**", cors);
		return sorgente;
	}

}
