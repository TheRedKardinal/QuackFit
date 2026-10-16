package it.quackfit.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import it.quackfit.utente.Utente;

/**
 * Crea e legge i JWT. Il token contiene l'id dell'utente (subject), il ruolo e la data di emissione.
 */
@Service
public class JwtService {

	private static final int LUNGHEZZA_MINIMA_SEGRETO = 32;

	private final SecretKey chiave;
	private final Duration durata;
	private final Clock clock;

	public JwtService(
			@Value("${quackfit.jwt.segreto}") String segreto,
			@Value("${quackfit.jwt.durata}") Duration durata,
			Clock clock) {
		if (segreto == null || segreto.length() < LUNGHEZZA_MINIMA_SEGRETO) {
			throw new IllegalStateException(
					"JWT_SECRET mancante o troppo corto: servono almeno " + LUNGHEZZA_MINIMA_SEGRETO + " caratteri");
		}
		this.chiave = Keys.hmacShaKeyFor(segreto.getBytes(StandardCharsets.UTF_8));
		this.durata = durata;
		this.clock = clock;
	}

	public String genera(Utente utente) {
		Instant adesso = Instant.now(clock);
		return Jwts.builder()
				.subject(utente.getId().toString())
				.claim("ruolo", utente.getRuolo().name())
				.issuedAt(Date.from(adesso))
				.expiration(Date.from(adesso.plus(durata)))
				.signWith(chiave)
				.compact();
	}

	/**
	 * Controlla firma e scadenza (con il Clock dell'applicazione).
	 * Token non valido per qualunque motivo → Optional vuoto, senza dettagli.
	 */
	public Optional<DatiToken> leggi(String token) {
		try {
			var claims = Jwts.parser()
					.verifyWith(chiave)
					.clock(() -> Date.from(Instant.now(clock)))
					.build()
					.parseSignedClaims(token)
					.getPayload();
			if (claims.getIssuedAt() == null) {
				return Optional.empty();
			}
			return Optional.of(new DatiToken(UUID.fromString(claims.getSubject()), claims.getIssuedAt().toInstant()));
		} catch (JwtException | IllegalArgumentException e) {
			// firma sbagliata, token scaduto o malformato, subject che non è un UUID
			return Optional.empty();
		}
	}

	/** Ciò che serve al filtro: di chi è il token e quando è stato emesso (in secondi). */
	public record DatiToken(UUID utenteId, Instant emessoIl) {
	}

}
