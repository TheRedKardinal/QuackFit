package it.quackfit.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Orologio dell'applicazione: si inietta ovunque serva la data o l'ora corrente,
 * così nei test si può sostituire con un orologio fisso.
 */
@Configuration
public class ClockConfig {

	/** Fuso orario unico per "oggi" e "ieri" (streak). */
	public static final ZoneId FUSO = ZoneId.of("Europe/Rome");

	/**
	 * Orologio di sistema sul fuso di Roma: {@code Instant.now(clock)} non dipende dal fuso,
	 * mentre {@code LocalDate.now(clock)} restituisce direttamente il giorno italiano.
	 */
	@Bean
	public Clock clock() {
		return Clock.system(FUSO);
	}

}
