package it.quackfit.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;
import it.quackfit.security.JwtService;
import it.quackfit.utente.Streak;
import it.quackfit.utente.StreakRepository;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/** Registrazione (e, nelle prossime task, login e recupero della password). Vedi auth.md. */
@Service
public class AuthService {

	static final String MSG_EMAIL_GIA_USATA =
			"Email già registrata. Se è tua, accedi oppure usa 'Password dimenticata'.";

	private final UtenteRepository utenteRepository;
	private final StreakRepository streakRepository;
	private final CodiceService codiceService;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final Clock clock;

	public AuthService(UtenteRepository utenteRepository, StreakRepository streakRepository, CodiceService codiceService,
			PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
		this.utenteRepository = utenteRepository;
		this.streakRepository = streakRepository;
		this.codiceService = codiceService;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.clock = clock;
	}

	/**
	 * Crea l'utente (con la sua streak a zero), invia il codice di verifica e restituisce il token.
	 * Un invio fallito non annulla nulla: MailService non lancia eccezioni.
	 */
	@Transactional
	public TokenResponse registra(RegistrazioneRequest richiesta) {
		if (utenteRepository.existsByEmail(richiesta.email())) {
			throw emailGiaUsata();
		}
		Instant adesso = Instant.now(clock);
		Utente utente = new Utente(richiesta.nome(), richiesta.cognome(), richiesta.email(),
				passwordEncoder.encode(richiesta.password()), adesso);
		try {
			// saveAndFlush: l'INSERT parte subito, così una registrazione simultanea con la stessa email
			// fa scattare qui il vincolo UNIQUE (l'unico di Utente) e diventa un 409 invece di un 500
			utente = utenteRepository.saveAndFlush(utente);
		} catch (DataIntegrityViolationException e) {
			throw emailGiaUsata();
		}
		streakRepository.save(new Streak(utente, adesso));
		codiceService.generaEInvia(utente, TipoCodice.VERIFICA_EMAIL, null);
		return new TokenResponse(jwtService.genera(utente));
	}

	private static ApplicazioneException emailGiaUsata() {
		return new ApplicazioneException(CodiceErrore.EMAIL_GIA_USATA, MSG_EMAIL_GIA_USATA);
	}

}
