package it.quackfit.utente;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.auth.CodiceVerificaRepository;
import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;

/**
 * Cancellazione del proprio account (vedi me.md, Cancellazione account). La riga Utente non si elimina, perché i
 * tentativi restano collegati: si anonimizza. Streak e codici invece si eliminano definitivamente.
 */
@Service
public class CancellazioneAccountService {

	static final String MSG_SUPERADMIN = "Il superadmin non può cancellare il proprio account";

	private final UtenteRepository utenteRepository;
	private final StreakRepository streakRepository;
	private final CodiceVerificaRepository codiceRepository;
	private final PasswordAttuale passwordAttuale;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher eventi;
	private final Clock clock;

	public CancellazioneAccountService(UtenteRepository utenteRepository, StreakRepository streakRepository,
			CodiceVerificaRepository codiceRepository, PasswordAttuale passwordAttuale, PasswordEncoder passwordEncoder,
			ApplicationEventPublisher eventi, Clock clock) {
		this.utenteRepository = utenteRepository;
		this.streakRepository = streakRepository;
		this.codiceRepository = codiceRepository;
		this.passwordAttuale = passwordAttuale;
		this.passwordEncoder = passwordEncoder;
		this.eventi = eventi;
		this.clock = clock;
	}

	/**
	 * Controlli, dopo @Valid: password attuale sbagliata → 400 PASSWORD_ATTUALE_ERRATA; SUPERADMIN → 403 ACCESSO_NEGATO.
	 * Poi, in una sola transazione e con un solo istante:
	 * - nome "Utente", cognome "Eliminato", email deleted-&lt;id&gt;@quackfit.local (quella vera torna libera),
	 *   password = hash di un valore casuale (nessuno può più fare login), niente foto, deletedAt e logout ovunque
	 * - Streak e CodiceVerifica eliminati; i tentativi restano
	 * La foto su Cloudinary si elimina dopo il commit (EliminazioneFotoProfilo), solo se c'era.
	 */
	@Transactional
	public void cancella(UUID utenteId, CancellazioneAccountRequest richiesta) {
		Utente utente = utenteRepository.findById(utenteId)
				.orElseThrow(() -> new ApplicazioneException(CodiceErrore.NON_TROVATO, "Utente non trovato"));
		passwordAttuale.controlla(richiesta.password(), utente);
		if (utente.getRuolo() == Ruolo.SUPERADMIN) {
			throw new ApplicazioneException(CodiceErrore.ACCESSO_NEGATO, MSG_SUPERADMIN);
		}

		boolean avevaFoto = utente.getFotoProfilo() != null;
		Instant adesso = Instant.now(clock);
		utente.setNome("Utente");
		utente.setCognome("Eliminato");
		utente.setEmail("deleted-" + utente.getId() + "@quackfit.local");
		utente.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
		utente.setFotoProfilo(null);
		utente.setDeletedAt(adesso);
		utente.setTokenValidiDal(adesso);

		streakRepository.deleteByUtente(utente);
		codiceRepository.deleteByUtente(utente);

		if (avevaFoto) {
			eventi.publishEvent(new FotoDaEliminare(AvatarService.idPubblico(utente.getId())));
		}
	}

}
