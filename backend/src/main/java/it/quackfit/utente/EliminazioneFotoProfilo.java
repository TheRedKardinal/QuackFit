package it.quackfit.utente;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import it.quackfit.config.ArchivioImmagini;
import it.quackfit.config.CaricamentoImmagineFallitoException;
import lombok.extern.slf4j.Slf4j;

/**
 * Elimina da Cloudinary la foto di un account cancellato, solo DOPO il commit della transazione (AFTER_COMMIT):
 * se l'anonimizzazione fallisce e viene annullata, la foto resta. Se è Cloudinary a fallire, l'account resta
 * comunque cancellato e l'errore finisce nel log (vedi me.md, Cancellazione account).
 */
@Slf4j
@Component
public class EliminazioneFotoProfilo {

	private final ArchivioImmagini archivio;

	public EliminazioneFotoProfilo(ArchivioImmagini archivio) {
		this.archivio = archivio;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void elimina(FotoDaEliminare evento) {
		try {
			archivio.elimina(evento.idPubblico());
		} catch (CaricamentoImmagineFallitoException e) {
			log.error("Eliminazione della foto di un account cancellato non riuscita: {}", e.getMessage());
		}
	}

}
