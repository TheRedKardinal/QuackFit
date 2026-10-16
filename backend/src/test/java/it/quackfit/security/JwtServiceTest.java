package it.quackfit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import it.quackfit.common.ClockConfig;
import it.quackfit.security.JwtService.DatiToken;
import it.quackfit.utente.Utente;

/** Unit test di JwtService, con orologi fissi per controllare emissione e scadenza. */
class JwtServiceTest {

	private static final String SEGRETO = "segreto-dei-test-quackfit-non-usare-altrove-0123456789";
	private static final Duration DURATA = Duration.ofHours(24);
	private static final Instant ADESSO = Instant.parse("2026-10-16T10:00:00.750Z");

	private static JwtService servizio(String segreto, Instant istante) {
		return new JwtService(segreto, DURATA, Clock.fixed(istante, ClockConfig.FUSO));
	}

	private static Utente utente() {
		Utente utente = new Utente("Mario", "Rossi", "mario@example.com", "hash", ADESSO);
		ReflectionTestUtils.setField(utente, "id", UUID.randomUUID());
		return utente;
	}

	@Test
	void tokenGeneratoSiRileggeConIdEEmissioneAlSecondo() {
		Utente utente = utente();
		JwtService servizio = servizio(SEGRETO, ADESSO);

		DatiToken dati = servizio.leggi(servizio.genera(utente)).orElseThrow();

		assertThat(dati.utenteId()).isEqualTo(utente.getId());
		// iat ha precisione al secondo: i millisecondi si perdono
		assertThat(dati.emessoIl()).isEqualTo(Instant.parse("2026-10-16T10:00:00Z"));
	}

	@Test
	void tokenValidoFinoAlleVentiquattroOre() {
		String token = servizio(SEGRETO, ADESSO).genera(utente());

		assertThat(servizio(SEGRETO, ADESSO.plus(Duration.ofHours(23))).leggi(token)).isPresent();
	}

	@Test
	void tokenScadutoDopoVentiquattroOre() {
		String token = servizio(SEGRETO, ADESSO).genera(utente());

		assertThat(servizio(SEGRETO, ADESSO.plus(Duration.ofHours(25))).leggi(token)).isEmpty();
	}

	@Test
	void tokenFirmatoConUnAltroSegretoRifiutato() {
		String token = servizio("un-altro-segreto-lungo-almeno-trentadue-caratteri", ADESSO).genera(utente());

		assertThat(servizio(SEGRETO, ADESSO).leggi(token)).isEmpty();
	}

	@Test
	void tokenMalformatoRifiutato() {
		assertThat(servizio(SEGRETO, ADESSO).leggi("non-un-jwt")).isEmpty();
	}

	@Test
	void segretoTroppoCortoBloccaLAvvio() {
		assertThatThrownBy(() -> servizio("corto", ADESSO))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_SECRET");
	}

}
