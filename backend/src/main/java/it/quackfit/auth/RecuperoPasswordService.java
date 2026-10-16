package it.quackfit.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/**
 * Password dimenticata e reimpostazione con il codice (vedi auth.md).
 * Nessuna delle due risposte rivela se un'email è registrata: né il messaggio, né il codice di errore, né i tempi.
 */
@Service
public class RecuperoPasswordService {

	static final String MSG_CODICE_NON_VALIDO = "Codice non valido o scaduto: richiedine uno nuovo";

	private final UtenteRepository utenteRepository;
	private final CodiceService codiceService;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public RecuperoPasswordService(UtenteRepository utenteRepository, CodiceService codiceService,
			PasswordEncoder passwordEncoder, Clock clock) {
		this.utenteRepository = utenteRepository;
		this.codiceService = codiceService;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/**
	 * Se l'utente esiste ed è attivo e sono passati 60 secondi dall'ultimo invio, genera e invia un codice
	 * RESET_PASSWORD; altrimenti non fa nulla, in silenzio.
	 *
	 * Tutto il lavoro è @Async: il controller risponde subito e la durata della risposta non dipende
	 * da ricerca, hash BCrypt del codice e invio. La transazione è dentro il metodo asincrono (gira nel suo thread).
	 * Funziona solo se il metodo è chiamato da un altro bean (passando dal proxy di Spring).
	 * Gli errori non arrivano al client: li logga l'handler di AsyncConfig.
	 */
	@Async
	@Transactional
	public void inviaCodice(String email) {
		Optional<Utente> trovato = utenteRepository.findByEmailAndDeletedAtIsNull(email);
		if (trovato.isEmpty() || !codiceService.reinvioConsentito(trovato.get(), TipoCodice.RESET_PASSWORD)) {
			return;
		}
		codiceService.generaEInvia(trovato.get(), TipoCodice.RESET_PASSWORD, null);
	}

	/**
	 * Verifica il codice e imposta la nuova password: email verificata, logout ovunque, login sbloccato.
	 *
	 * Ogni fallimento (email inesistente, codice sbagliato, scaduto, tentativi esauriti) risponde con lo stesso
	 * 400 CODICE_NON_VALIDO: gli errori distinti rivelerebbero che l'email è registrata, perché chiunque può
	 * far generare un codice per un'email altrui. Il +1 ai tentativi resta salvato dalla transazione propria
	 * di CodiceService.verifica, anche se qui si lancia l'errore.
	 *
	 * Lock pessimistico sull'utente: un reset in parallelo a un login non perde gli aggiornamenti dei contatori.
	 */
	@Transactional
	public void reimposta(ReimpostaPasswordRequest richiesta) {
		Optional<Utente> trovato = utenteRepository.findPerLoginByEmailAndDeletedAtIsNull(richiesta.email());
		if (trovato.isEmpty()) {
			codiceService.confrontoFittizio(richiesta.codice());
			throw codiceNonValido();
		}
		Utente utente = trovato.get();

		try {
			codiceService.verifica(utente, TipoCodice.RESET_PASSWORD, richiesta.codice());
		} catch (ApplicazioneException e) {
			throw codiceNonValido();
		}

		utente.setPassword(passwordEncoder.encode(richiesta.nuovaPassword()));
		utente.setEmailVerificata(true);
		utente.setTokenValidiDal(Instant.now(clock));
		utente.setLoginFalliti(0);
		utente.setPrimoLoginFallitoAt(null);
		utente.setBloccatoFino(null);
	}

	private static ApplicazioneException codiceNonValido() {
		return new ApplicazioneException(CodiceErrore.CODICE_NON_VALIDO, MSG_CODICE_NON_VALIDO);
	}

}
