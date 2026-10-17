package it.quackfit.utente;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.auth.CodiceService;
import it.quackfit.auth.CodiceVerificato;
import it.quackfit.auth.TipoCodice;
import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;

/**
 * Cambio email in due passaggi (vedi me.md, Cambio email): l'email cambia solo quando il nuovo indirizzo è
 * confermato con il codice. Si lavora sull'utente ricaricato per id, non sul principal.
 */
@Service
public class CambioEmailService {

	static final String MSG_EMAIL_UGUALE = "La nuova email deve essere diversa da quella attuale";
	static final String MSG_EMAIL_GIA_USATA = "Questa email è già usata da un altro account";

	private final UtenteRepository utenteRepository;
	private final PasswordAttuale passwordAttuale;
	private final CodiceService codiceService;
	private final Clock clock;

	public CambioEmailService(UtenteRepository utenteRepository, PasswordAttuale passwordAttuale,
			CodiceService codiceService, Clock clock) {
		this.utenteRepository = utenteRepository;
		this.passwordAttuale = passwordAttuale;
		this.codiceService = codiceService;
		this.clock = clock;
	}

	/**
	 * Richiesta. La nuova email è già pulita (strip, minuscolo) e valida (@Valid sul record). Poi, in quest'ordine:
	 * 1. password attuale sbagliata → 400 PASSWORD_ATTUALE_ERRATA
	 * 2. nuova email uguale all'attuale → 400 VALIDAZIONE sul campo nuovaEmail
	 * 3. nuova email già usata → 409 EMAIL_GIA_USATA
	 * 4. meno di 60 secondi dall'ultimo codice CAMBIO_EMAIL → 429 ATTENDI_REINVIO (da CodiceService)
	 * I punti 2-4 vengono dopo la password: senza conoscerla, un token rubato non rivela quali email sono registrate.
	 * Se tutto va bene il codice parte verso la NUOVA email; quella dell'utente non cambia ancora.
	 */
	@Transactional
	public void richiedi(UUID utenteId, CambioEmailRequest richiesta) {
		Utente utente = carica(utenteId);
		passwordAttuale.controlla(richiesta.password(), utente);
		if (richiesta.nuovaEmail().equals(utente.getEmail())) {
			throw new ApplicazioneException(CodiceErrore.VALIDAZIONE, "Uno o più campi non sono validi",
					Map.of("nuovaEmail", MSG_EMAIL_UGUALE));
		}
		if (utenteRepository.existsByEmail(richiesta.nuovaEmail())) {
			throw emailGiaUsata();
		}
		codiceService.generaEInvia(utente, TipoCodice.CAMBIO_EMAIL, richiesta.nuovaEmail());
	}

	/**
	 * Conferma. Il codice si verifica nella transazione propria di CodiceService: il +1 ai tentativi resta salvato,
	 * e un codice giusto resta "usato" anche se poi si risponde 409 (l'utente ne chiede uno nuovo, vedi auth.md).
	 * Se nel frattempo la nuova email è stata presa → 409 EMAIL_GIA_USATA, anche quando due conferme arrivano insieme:
	 * saveAndFlush fa partire subito la UPDATE e il vincolo UNIQUE diventa un 409 invece di un 500 (come in registrazione).
	 * Se va bene: nuova email, email verificata (l'utente ha dimostrato di riceverla) e logout ovunque.
	 */
	@Transactional
	public void conferma(UUID utenteId, String codice) {
		Utente utente = carica(utenteId);
		CodiceVerificato verificato = codiceService.verifica(utente, TipoCodice.CAMBIO_EMAIL, codice);
		String nuovaEmail = verificato.nuovaEmail();
		if (utenteRepository.existsByEmail(nuovaEmail)) {
			throw emailGiaUsata();
		}
		utente.setEmail(nuovaEmail);
		utente.setEmailVerificata(true);
		utente.setTokenValidiDal(Instant.now(clock));
		try {
			utenteRepository.saveAndFlush(utente);
		} catch (DataIntegrityViolationException e) {
			throw emailGiaUsata();
		}
	}

	private Utente carica(UUID utenteId) {
		return utenteRepository.findById(utenteId)
				.orElseThrow(() -> new ApplicazioneException(CodiceErrore.NON_TROVATO, "Utente non trovato"));
	}

	private static ApplicazioneException emailGiaUsata() {
		return new ApplicazioneException(CodiceErrore.EMAIL_GIA_USATA, MSG_EMAIL_GIA_USATA);
	}

}
