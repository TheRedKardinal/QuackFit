package it.quackfit.utente;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.common.ClockConfig;
import it.quackfit.tentativi.TentativoLezioneRepository;

/**
 * Profilo dell'utente loggato (vedi me.md, Dati del profilo). Solo lettura: si usano i dati del principal,
 * la streak salvata non viene mai modificata.
 */
@Service
public class MeService {

	private final StreakRepository streakRepository;
	private final TentativoLezioneRepository tentativoRepository;
	private final Clock clock;

	public MeService(StreakRepository streakRepository, TentativoLezioneRepository tentativoRepository, Clock clock) {
		this.streakRepository = streakRepository;
		this.tentativoRepository = tentativoRepository;
		this.clock = clock;
	}

	/**
	 * Ogni utente attivo ha la sua Streak (registrazione e seeder): se manca è un dato incoerente,
	 * quindi 500 con messaggio generico; nel log finisce solo l'id dell'utente.
	 */
	@Transactional(readOnly = true)
	public MeResponse profilo(Utente utente) {
		Streak streak = streakRepository.findByUtente(utente)
				.orElseThrow(() -> new IllegalStateException("Streak mancante per l'utente " + utente.getId()));
		int lezioniCompletate = tentativoRepository.lezioniCompletate(utente.getId()).size();
		return MeResponse.da(utente, streakAttuale(streak), streak.getRecord(), lezioniCompletate);
	}

	/**
	 * Valore da mostrare: quello salvato se l'ultima attività è oggi o ieri (fuso Europe/Rome), altrimenti 0.
	 * Il giorno si ricava dall'istante con il fuso esplicito, così non dipende dal fuso dell'orologio.
	 */
	private int streakAttuale(Streak streak) {
		LocalDate oggi = LocalDate.ofInstant(Instant.now(clock), ClockConfig.FUSO);
		LocalDate ultimaAttivita = streak.getUltimaAttivita();
		if (ultimaAttivita != null && !ultimaAttivita.isBefore(oggi.minusDays(1))) {
			return streak.getValore();
		}
		return 0;
	}

}
