package it.quackfit.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;
import it.quackfit.mail.MailService;
import it.quackfit.utente.Utente;

/**
 * Generazione, invio e verifica dei codici a 6 cifre (vedi auth.md, Codici email).
 * Riusato da registrazione, verifica email, password dimenticata e cambio email.
 */
@Service
public class CodiceService {

	static final Duration ATTESA_REINVIO = Duration.ofSeconds(60);
	static final int MASSIMO_TENTATIVI = 5;

	static final String MSG_NON_VALIDO = "Codice non valido";
	static final String MSG_SCADUTO = "Codice scaduto: richiedine uno nuovo";
	static final String MSG_TROPPI_TENTATIVI = "Troppi tentativi: richiedi un nuovo codice";
	static final String MSG_ATTENDI_REINVIO = "Attendi un minuto prima di richiedere un nuovo codice";

	private final CodiceVerificaRepository codiceRepository;
	private final PasswordEncoder passwordEncoder;
	private final MailService mailService;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public CodiceService(CodiceVerificaRepository codiceRepository, PasswordEncoder passwordEncoder,
			MailService mailService, Clock clock) {
		this.codiceRepository = codiceRepository;
		this.passwordEncoder = passwordEncoder;
		this.mailService = mailService;
		this.clock = clock;
	}

	/**
	 * true se sono passati almeno 60 secondi dalla creazione dell'ultimo codice dello stesso tipo (usato o no).
	 * Serve a chi deve tacere invece di rispondere 429 (password dimenticata).
	 */
	@Transactional(readOnly = true)
	public boolean reinvioConsentito(Utente utente, TipoCodice tipo) {
		return reinvioConsentito(utente, tipo, Instant.now(clock));
	}

	/**
	 * Invalida i codici precedenti dello stesso tipo, genera il nuovo, salva solo l'hash e invia l'email.
	 * Per CAMBIO_EMAIL serve {@code nuovaEmail}, che è anche il destinatario; per gli altri tipi va null.
	 * Il codice in chiaro non esce da questo metodo.
	 *
	 * @throws ApplicazioneException ATTENDI_REINVIO se l'ultimo invio è di meno di 60 secondi fa
	 */
	@Transactional
	public void generaEInvia(Utente utente, TipoCodice tipo, String nuovaEmail) {
		if ((tipo == TipoCodice.CAMBIO_EMAIL) != (nuovaEmail != null)) {
			throw new IllegalArgumentException("nuovaEmail serve solo (e sempre) per CAMBIO_EMAIL");
		}
		Instant adesso = Instant.now(clock);
		if (!reinvioConsentito(utente, tipo, adesso)) {
			throw new ApplicazioneException(CodiceErrore.ATTENDI_REINVIO, MSG_ATTENDI_REINVIO);
		}

		// vale solo l'ultimo: i precedenti ancora validi diventano "usati"
		for (CodiceVerifica vecchio : codiceRepository.findByUtenteAndTipoAndUsatoAtIsNull(utente, tipo)) {
			vecchio.setUsatoAt(adesso);
		}

		String codice = seiCifre(random.nextInt(1_000_000));
		codiceRepository.save(new CodiceVerifica(utente, tipo, passwordEncoder.encode(codice), nuovaEmail, adesso));

		switch (tipo) {
			case VERIFICA_EMAIL -> mailService.inviaVerificaEmail(utente.getEmail(), utente.getNome(), codice);
			case RESET_PASSWORD -> mailService.inviaResetPassword(utente.getEmail(), utente.getNome(), codice);
			case CAMBIO_EMAIL -> mailService.inviaConfermaCambioEmail(nuovaEmail, utente.getNome(), codice);
		}
	}

	/**
	 * Verifica il codice nell'ordine di auth.md e, se è giusto, lo marca come usato.
	 *
	 * Transazione propria e niente rollback sugli errori dei codici: il +1 ai tentativi resta salvato
	 * anche se si lancia l'errore e anche se poi il chiamante annulla la sua transazione.
	 * - REQUIRES_NEW: la transazione è indipendente da quella del chiamante;
	 * - noRollbackFor: lanciare CODICE_NON_VALIDO / TROPPI_TENTATIVI non annulla il conteggio.
	 * Funziona solo se il metodo è chiamato da un altro bean (passando dal proxy di Spring).
	 *
	 * @return solo l'esito (per CAMBIO_EMAIL la nuova email), mai l'entità
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApplicazioneException.class)
	public CodiceVerificato verifica(Utente utente, TipoCodice tipo, String codice) {
		Instant adesso = Instant.now(clock);
		CodiceVerifica valido = codiceRepository.findFirstByUtenteAndTipoAndUsatoAtIsNullOrderByCreatedAtDesc(utente, tipo)
				.orElseThrow(() -> new ApplicazioneException(CodiceErrore.CODICE_NON_VALIDO, MSG_NON_VALIDO));

		if (!adesso.isBefore(valido.getScadenza())) {
			throw new ApplicazioneException(CodiceErrore.CODICE_SCADUTO, MSG_SCADUTO);
		}
		if (valido.getTentativiFalliti() >= MASSIMO_TENTATIVI) {
			throw new ApplicazioneException(CodiceErrore.TROPPI_TENTATIVI, MSG_TROPPI_TENTATIVI);
		}
		if (codice == null || !passwordEncoder.matches(codice, valido.getCodiceHash())) {
			valido.setTentativiFalliti(valido.getTentativiFalliti() + 1);
			if (valido.getTentativiFalliti() >= MASSIMO_TENTATIVI) {
				throw new ApplicazioneException(CodiceErrore.TROPPI_TENTATIVI, MSG_TROPPI_TENTATIVI);
			}
			throw new ApplicazioneException(CodiceErrore.CODICE_NON_VALIDO, MSG_NON_VALIDO);
		}

		valido.setUsatoAt(adesso);
		return new CodiceVerificato(valido.getNuovaEmail());
	}

	/** Sempre 6 cifre, anche per i numeri piccoli (es. 4213 → "004213"). */
	static String seiCifre(int numero) {
		return String.format("%06d", numero);
	}

	private boolean reinvioConsentito(Utente utente, TipoCodice tipo, Instant adesso) {
		return codiceRepository.findFirstByUtenteAndTipoOrderByCreatedAtDesc(utente, tipo)
				.map(ultimo -> !adesso.isBefore(ultimo.getCreatedAt().plus(ATTESA_REINVIO)))
				.orElse(true);
	}

}
