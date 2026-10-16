package it.quackfit.utente;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.auth.CodiceService;
import it.quackfit.auth.TipoCodice;
import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;

/**
 * Verifica dell'email con il codice e invio di un nuovo codice (vedi auth.md, Verifica email e Nuovo codice).
 * Si lavora sull'utente ricaricato per id, non sul principal, che è "staccato" dal database.
 */
@Service
public class VerificaEmailService {

	private final UtenteRepository utenteRepository;
	private final CodiceService codiceService;

	public VerificaEmailService(UtenteRepository utenteRepository, CodiceService codiceService) {
		this.utenteRepository = utenteRepository;
		this.codiceService = codiceService;
	}

	/**
	 * Email già verificata → nessuna modifica. Altrimenti verifica il codice e segna l'email come verificata.
	 * Un codice sbagliato lancia l'errore di CodiceService: il +1 ai tentativi è già salvato dalla sua
	 * transazione propria, quindi il rollback di questa non lo annulla.
	 */
	@Transactional
	public void verifica(UUID utenteId, String codice) {
		Utente utente = carica(utenteId);
		if (utente.isEmailVerificata()) {
			return;
		}
		codiceService.verifica(utente, TipoCodice.VERIFICA_EMAIL, codice);
		utente.setEmailVerificata(true);
	}

	/**
	 * Email già verificata → non invia nulla. Altrimenti genera e invia un nuovo codice:
	 * entro 60 secondi dal precedente CodiceService risponde 429 ATTENDI_REINVIO.
	 */
	@Transactional
	public void reinvia(UUID utenteId) {
		Utente utente = carica(utenteId);
		if (utente.isEmailVerificata()) {
			return;
		}
		codiceService.generaEInvia(utente, TipoCodice.VERIFICA_EMAIL, null);
	}

	private Utente carica(UUID utenteId) {
		return utenteRepository.findById(utenteId)
				.orElseThrow(() -> new ApplicazioneException(CodiceErrore.NON_TROVATO, "Utente non trovato"));
	}

}
