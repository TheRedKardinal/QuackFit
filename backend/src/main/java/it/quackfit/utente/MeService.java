package it.quackfit.utente;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.ClockConfig;
import it.quackfit.common.CodiceErrore;
import it.quackfit.common.PasswordValidaValidator;
import it.quackfit.tentativi.TentativoLezioneRepository;

/**
 * Profilo dell'utente loggato (vedi me.md: Dati del profilo, Modifica dati, Cambio password).
 * In lettura si usano i dati del principal; per scrivere si ricarica l'utente per id dentro la transazione
 * (il principal è staccato dal database, vedi backend/CLAUDE.md).
 */
@Service
public class MeService {

	static final String MSG_PASSWORD_ATTUALE_ERRATA = "La password attuale non è corretta";
	static final String MSG_PASSWORD_UGUALE = "La nuova password deve essere diversa da quella attuale";

	private final UtenteRepository utenteRepository;
	private final StreakRepository streakRepository;
	private final TentativoLezioneRepository tentativoRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public MeService(UtenteRepository utenteRepository, StreakRepository streakRepository,
			TentativoLezioneRepository tentativoRepository, PasswordEncoder passwordEncoder, Clock clock) {
		this.utenteRepository = utenteRepository;
		this.streakRepository = streakRepository;
		this.tentativoRepository = tentativoRepository;
		this.passwordEncoder = passwordEncoder;
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

	/** Nome e cognome, già puliti e validati nel record. */
	@Transactional
	public void modificaDati(UUID utenteId, ModificaDatiRequest richiesta) {
		Utente utente = carica(utenteId);
		utente.setNome(richiesta.nome());
		utente.setCognome(richiesta.cognome());
	}

	/**
	 * Cambio password. La nuova è già valida (@Valid sul record). Poi, in quest'ordine:
	 * 1. password attuale sbagliata → 400 PASSWORD_ATTUALE_ERRATA; oltre i 72 byte conta come sbagliata
	 *    senza chiamare BCrypt (che lancerebbe un'eccezione)
	 * 2. nuova uguale all'attuale → 400 VALIDAZIONE sul campo nuovaPassword. Viene dopo il punto 1, così non
	 *    rivela se una password è quella attuale; l'attuale è appena stata verificata, basta confrontare i testi
	 * 3. nuovo hash e logout ovunque (tokenValidiDal = adesso): anche il token di questa richiesta smette di valere
	 */
	@Transactional
	public void cambiaPassword(UUID utenteId, CambioPasswordRequest richiesta) {
		Utente utente = carica(utenteId);
		if (!passwordGiusta(richiesta.passwordAttuale(), utente.getPassword())) {
			throw new ApplicazioneException(CodiceErrore.PASSWORD_ATTUALE_ERRATA, MSG_PASSWORD_ATTUALE_ERRATA);
		}
		if (richiesta.nuovaPassword().equals(richiesta.passwordAttuale())) {
			throw new ApplicazioneException(CodiceErrore.VALIDAZIONE, "Uno o più campi non sono validi",
					Map.of("nuovaPassword", MSG_PASSWORD_UGUALE));
		}
		utente.setPassword(passwordEncoder.encode(richiesta.nuovaPassword()));
		utente.setTokenValidiDal(Instant.now(clock));
	}

	private boolean passwordGiusta(String password, String hash) {
		boolean troppoLunga = password.getBytes(StandardCharsets.UTF_8).length > PasswordValidaValidator.MASSIMO_BYTE;
		return !troppoLunga && passwordEncoder.matches(password, hash);
	}

	private Utente carica(UUID utenteId) {
		return utenteRepository.findById(utenteId)
				.orElseThrow(() -> new ApplicazioneException(CodiceErrore.NON_TROVATO, "Utente non trovato"));
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
