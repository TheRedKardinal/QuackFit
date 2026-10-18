package it.quackfit.contenuti;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;
import it.quackfit.contenuti.CreazioneDomandaRequest.CoppiaRequest;
import it.quackfit.contenuti.CreazioneDomandaRequest.OpzioneRequest;
import it.quackfit.contenuti.ModificaDomandaRequest.TestoRequest;

/**
 * Gestione delle domande da parte degli admin (vedi admin.md, Domande). Le regole del tipo e i testi ripetuti sono in
 * {@link RegoleDomanda}. Creazione e cancellazione cambiano l'ordine delle domande della lezione: bloccano prima la
 * riga della lezione genitore ({@link LezioneRepository#blocca}) e poi leggono e rinumerano, come tappe e lezioni.
 */
@Service
public class AdminDomandeService {

	private final LezioneRepository lezioneRepository;
	private final DomandaRepository domandaRepository;
	private final Clock clock;

	public AdminDomandeService(LezioneRepository lezioneRepository, DomandaRepository domandaRepository, Clock clock) {
		this.lezioneRepository = lezioneRepository;
		this.domandaRepository = domandaRepository;
		this.clock = clock;
	}

	/**
	 * Nuova domanda in fondo alla lezione. Prima le regole del tipo (400), poi il lock sulla lezione: se è
	 * inesistente o cancellata (anche mentre si aspettava il lock) → 404 e non si crea nulla.
	 */
	@Transactional
	public DomandaAdminResponse crea(UUID lezioneId, CreazioneDomandaRequest richiesta) {
		erroriDiValidazione(RegoleDomanda.creazione(richiesta));
		Lezione lezione = lezioneRepository.blocca(lezioneId)
				.orElseThrow(() -> nonTrovato("Lezione non trovata"));
		Domanda domanda = new Domanda(lezione, richiesta.testo(), richiesta.spiegazione(), richiesta.tipo(),
				domandaRepository.ordineMassimo(lezione) + 1, Instant.now(clock));
		switch (richiesta.tipo()) {
			case VERO_FALSO -> {
				domanda.aggiungiOpzione("Vero", richiesta.rispostaVero());
				domanda.aggiungiOpzione("Falso", !richiesta.rispostaVero());
			}
			case SCELTA_MULTIPLA -> {
				for (OpzioneRequest opzione : richiesta.opzioni()) {
					domanda.aggiungiOpzione(opzione.testo(), opzione.corretta());
				}
			}
			case ABBINAMENTO -> {
				for (CoppiaRequest coppia : richiesta.coppie()) {
					domanda.aggiungiCoppia(coppia.sinistra(), coppia.destra());
				}
			}
		}
		return DomandaAdminResponse.da(domandaRepository.save(domanda));
	}

	/** Solo i testi: i campi null restano invariati, la struttura non cambia mai. */
	@Transactional
	public DomandaAdminResponse modifica(UUID domandaId, ModificaDomandaRequest richiesta) {
		Domanda domanda = domandaRepository.findById(domandaId)
				.orElseThrow(() -> nonTrovato("Domanda non trovata"));
		erroriDiValidazione(RegoleDomanda.modifica(domanda, richiesta));
		if (richiesta.testo() != null) {
			domanda.setTesto(richiesta.testo());
		}
		if (richiesta.spiegazione() != null) {
			domanda.setSpiegazione(richiesta.spiegazione());
		}
		if (richiesta.opzioni() != null) {
			for (TestoRequest nuovo : richiesta.opzioni()) {
				domanda.getOpzioni().stream()
						.filter(opzione -> opzione.getId().equals(nuovo.id()))
						.forEach(opzione -> opzione.setTesto(nuovo.testo()));
			}
		}
		if (richiesta.elementi() != null) {
			for (TestoRequest nuovo : richiesta.elementi()) {
				domanda.getElementi().stream()
						.filter(elemento -> elemento.getId().equals(nuovo.id()))
						.forEach(elemento -> elemento.setTesto(nuovo.testo()));
			}
		}
		return DomandaAdminResponse.da(domanda);
	}

	/**
	 * Soft delete della domanda e rinumerazione delle altre della lezione (prima della UPDATE, che svuota il contesto
	 * di persistenza). Si può cancellare anche l'ultima: la lezione resta, ma senza domande non si avvia.
	 */
	@Transactional
	public void cancella(UUID domandaId) {
		UUID lezioneId = domandaRepository.lezioneIdDi(domandaId)
				.orElseThrow(() -> nonTrovato("Domanda non trovata"));
		// Una lezione cancellata nel frattempo ha cancellato anche la domanda
		Lezione lezione = lezioneRepository.blocca(lezioneId).orElseThrow(() -> nonTrovato("Domanda non trovata"));
		List<Domanda> domande = domandaRepository.findByLezioneOrderByOrdineAsc(lezione);
		Domanda domanda = domande.stream()
				.filter(d -> d.getId().equals(domandaId))
				.findFirst()
				.orElseThrow(() -> nonTrovato("Domanda non trovata"));
		List<Domanda> rimaste = new ArrayList<>(domande);
		rimaste.remove(domanda);
		Ordinamento.rinumera(rimaste, Domanda::setOrdine);
		domandaRepository.cancella(domandaId, Instant.now(clock));
	}

	private static void erroriDiValidazione(Map<String, String> campi) {
		if (!campi.isEmpty()) {
			throw new ApplicazioneException(CodiceErrore.VALIDAZIONE, "Uno o più campi non sono validi", campi);
		}
	}

	private static ApplicazioneException nonTrovato(String messaggio) {
		return new ApplicazioneException(CodiceErrore.NON_TROVATO, messaggio);
	}

}
