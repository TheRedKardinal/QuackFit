package it.quackfit.utente;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;
import it.quackfit.common.PaginaResponse;

/** Gestione degli utenti da parte degli admin (vedi admin.md, Utenti). */
@Service
public class AdminUtentiService {

	static final String MSG_ASSEGNA_SUPERADMIN = "Il ruolo di superadmin non si può assegnare";
	static final String MSG_SE_STESSO = "Non puoi cambiare il tuo ruolo";
	static final String MSG_SUPERADMIN = "Il ruolo del superadmin non si può cambiare";
	static final String MSG_SOLO_SUPERADMIN = "Solo il superadmin può togliere il ruolo di admin";

	/**
	 * Dal più recente; a parità di data l'id, così due utenti creati nello stesso istante non cambiano posto
	 * tra una pagina e l'altra.
	 */
	private static final Sort ORDINE = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final UtenteRepository utenteRepository;
	private final Clock clock;

	public AdminUtentiService(UtenteRepository utenteRepository, Clock clock) {
		this.utenteRepository = utenteRepository;
		this.clock = clock;
	}

	/**
	 * Utenti attivi, una pagina alla volta (page e size già controllati dal controller). `q` vuoto o di soli spazi
	 * vuol dire nessuna ricerca; altrimenti si cerca il testo dentro nome, cognome o email, senza distinguere
	 * maiuscole e minuscole. I caratteri % e _ del testo si cercano come tali, non come jolly del LIKE.
	 */
	@Transactional(readOnly = true)
	public PaginaResponse<UtenteAdminResponse> lista(int pagina, int dimensione, String q) {
		PageRequest richiesta = PageRequest.of(pagina, dimensione, ORDINE);
		String testo = q == null ? "" : q.strip();
		Page<Utente> risultati = testo.isEmpty()
				? utenteRepository.findByDeletedAtIsNull(richiesta)
				: utenteRepository.cercaAttivi(modelloLike(testo), richiesta);
		return PaginaResponse.da(risultati, UtenteAdminResponse::da);
	}

	/** "%testo%" in minuscolo, con '!' come carattere di escape per !, % e _. */
	static String modelloLike(String testo) {
		String protetto = testo.toLowerCase(Locale.ROOT)
				.replace("!", "!!")
				.replace("%", "!%")
				.replace("_", "!_");
		return "%" + protetto + "%";
	}

	/**
	 * Cambio ruolo, con i controlli nell'ordine di admin.md (dopo @Valid sul body):
	 * 1. utente inesistente o cancellato → 404 NON_TROVATO
	 * 2. SUPERADMIN nel body → 403 (nessuno lo assegna)
	 * 3. il chiamante stesso → 403
	 * 4. un SUPERADMIN → 403 (nessuno gli toglie il ruolo)
	 * 5. stesso ruolo → nessuna modifica e nessun logout
	 * 6. un ADMIN non può retrocedere un altro ADMIN (solo il SUPERADMIN può)
	 * 7. ruolo aggiornato e logout ovunque per l'utente modificato, così il nuovo ruolo vale subito
	 */
	@Transactional
	public void cambiaRuolo(Utente chiamante, UUID utenteId, Ruolo nuovoRuolo) {
		Utente utente = utenteRepository.findById(utenteId)
				.filter(trovato -> trovato.getDeletedAt() == null)
				.orElseThrow(() -> new ApplicazioneException(CodiceErrore.NON_TROVATO, "Utente non trovato"));
		if (nuovoRuolo == Ruolo.SUPERADMIN) {
			throw accessoNegato(MSG_ASSEGNA_SUPERADMIN);
		}
		if (utente.getId().equals(chiamante.getId())) {
			throw accessoNegato(MSG_SE_STESSO);
		}
		if (utente.getRuolo() == Ruolo.SUPERADMIN) {
			throw accessoNegato(MSG_SUPERADMIN);
		}
		if (utente.getRuolo() == nuovoRuolo) {
			return;
		}
		boolean retrocessione = utente.getRuolo() == Ruolo.ADMIN && nuovoRuolo == Ruolo.UTENTE;
		if (retrocessione && chiamante.getRuolo() != Ruolo.SUPERADMIN) {
			throw accessoNegato(MSG_SOLO_SUPERADMIN);
		}
		Instant adesso = Instant.now(clock);
		utente.setRuolo(nuovoRuolo);
		utente.setTokenValidiDal(adesso);
	}

	private static ApplicazioneException accessoNegato(String messaggio) {
		return new ApplicazioneException(CodiceErrore.ACCESSO_NEGATO, messaggio);
	}

}
