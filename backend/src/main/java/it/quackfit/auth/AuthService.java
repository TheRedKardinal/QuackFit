package it.quackfit.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;
import it.quackfit.common.PasswordValidaValidator;
import it.quackfit.security.JwtService;
import it.quackfit.utente.Streak;
import it.quackfit.utente.StreakRepository;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/** Registrazione e login (e, nelle prossime task, recupero della password). Vedi auth.md. */
@Service
public class AuthService {

	static final String MSG_EMAIL_GIA_USATA =
			"Email già registrata. Se è tua, accedi oppure usa 'Password dimenticata'.";
	static final String MSG_CREDENZIALI_NON_VALIDE =
			"Email o password non corretti, oppure troppi tentativi: riprova tra qualche minuto.";

	/** Dopo questo tempo dal primo errore la serie di login falliti riparte da capo. */
	static final Duration DURATA_SERIE = Duration.ofMinutes(15);
	/** 3 errori entro questo tempo dal primo = comportamento da bot. */
	static final Duration FINESTRA_BOT = Duration.ofSeconds(10);
	static final int ERRORI_BOT = 3;
	static final int ERRORI_MASSIMI = 5;
	static final Duration DURATA_BLOCCO = Duration.ofMinutes(15);

	private final UtenteRepository utenteRepository;
	private final StreakRepository streakRepository;
	private final CodiceService codiceService;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final Clock clock;

	/**
	 * Hash di una password casuale, calcolato all'avvio con lo stesso PasswordEncoder (stesso costo BCrypt):
	 * quando l'utente non esiste o è bloccato si confronta la password con questo, così il tempo di risposta
	 * non rivela se l'email è registrata.
	 */
	private final String hashFittizio;

	public AuthService(UtenteRepository utenteRepository, StreakRepository streakRepository, CodiceService codiceService,
			PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
		this.utenteRepository = utenteRepository;
		this.streakRepository = streakRepository;
		this.codiceService = codiceService;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.clock = clock;
		this.hashFittizio = passwordEncoder.encode(UUID.randomUUID().toString());
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

	/**
	 * Login con i contatori dei tentativi falliti (vedi auth.md, Login e Contatori).
	 * Ogni fallimento risponde con lo stesso 401, qualunque sia il motivo.
	 *
	 * noRollbackFor: i contatori aggiornati restano salvati anche se poi si lancia il 401
	 * (altrimenti il rollback li cancellerebbe e il blocco non scatterebbe mai).
	 */
	@Transactional(noRollbackFor = ApplicazioneException.class)
	public TokenResponse login(LoginRequest richiesta) {
		Instant adesso = Instant.now(clock);
		// oltre i 72 byte BCrypt lancerebbe un'eccezione: la password conta come sbagliata e non si confronta
		boolean troppoLunga = richiesta.password().getBytes(StandardCharsets.UTF_8).length
				> PasswordValidaValidator.MASSIMO_BYTE;

		// lock pessimistico: i login sullo stesso account si mettono in fila (utente cancellato = non trovato)
		Optional<Utente> trovato = utenteRepository.findPerLoginByEmailAndDeletedAtIsNull(richiesta.email());
		if (trovato.isEmpty()) {
			confrontoFittizio(richiesta.password(), troppoLunga);
			throw credenzialiNonValide();
		}
		Utente utente = trovato.get();

		if (utente.getBloccatoFino() != null && adesso.isBefore(utente.getBloccatoFino())) {
			// bloccato: la password vera non si controlla e i contatori non cambiano
			confrontoFittizio(richiesta.password(), troppoLunga);
			throw credenzialiNonValide();
		}

		boolean passwordGiusta = !troppoLunga && passwordEncoder.matches(richiesta.password(), utente.getPassword());
		if (!passwordGiusta) {
			registraErrore(utente, adesso);
			throw credenzialiNonValide();
		}

		utente.setLoginFalliti(0);
		utente.setPrimoLoginFallitoAt(null);
		utente.setBloccatoFino(null);
		return new TokenResponse(jwtService.genera(utente));
	}

	/** I 4 passi della sezione "Contatori dei login falliti" di auth.md. */
	private static void registraErrore(Utente utente, Instant adesso) {
		Instant primo = utente.getPrimoLoginFallitoAt();
		if (primo == null || adesso.isAfter(primo.plus(DURATA_SERIE))) {
			utente.setLoginFalliti(1);
			utente.setPrimoLoginFallitoAt(adesso);
		} else {
			utente.setLoginFalliti(utente.getLoginFalliti() + 1);
		}

		boolean daBot = utente.getLoginFalliti() >= ERRORI_BOT
				&& !adesso.isAfter(utente.getPrimoLoginFallitoAt().plus(FINESTRA_BOT));
		if (daBot || utente.getLoginFalliti() >= ERRORI_MASSIMI) {
			utente.setBloccatoFino(adesso.plus(DURATA_BLOCCO));
			utente.setLoginFalliti(0);
			utente.setPrimoLoginFallitoAt(null);
		}
	}

	/** Stesso lavoro di un confronto vero, per non rivelare con i tempi se l'utente esiste. */
	private void confrontoFittizio(String password, boolean troppoLunga) {
		if (!troppoLunga) {
			passwordEncoder.matches(password, hashFittizio);
		}
	}

	private static ApplicazioneException emailGiaUsata() {
		return new ApplicazioneException(CodiceErrore.EMAIL_GIA_USATA, MSG_EMAIL_GIA_USATA);
	}

	private static ApplicazioneException credenzialiNonValide() {
		return new ApplicazioneException(CodiceErrore.CREDENZIALI_NON_VALIDE, MSG_CREDENZIALI_NON_VALIDE);
	}

}
