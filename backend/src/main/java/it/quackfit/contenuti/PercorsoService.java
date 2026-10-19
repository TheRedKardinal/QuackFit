package it.quackfit.contenuti;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;
import it.quackfit.contenuti.LezioneContenutoResponse.DomandaLezioneResponse;
import it.quackfit.contenuti.LezioneContenutoResponse.VoceResponse;
import it.quackfit.tentativi.TentativoLezioneRepository;

/**
 * Percorso dell'utente loggato (percorso.md): la mappa con lo stato delle lezioni e il contenuto di una lezione.
 * Contano solo tappe, lezioni e domande attive (@SQLRestriction); lo stato non è salvato, si calcola a ogni richiesta,
 * così se un admin inserisce, sposta o cancella una lezione il percorso si ricalcola da solo.
 */
@Service
public class PercorsoService {

	private final TappaRepository tappaRepository;
	private final LezioneRepository lezioneRepository;
	private final DomandaRepository domandaRepository;
	private final TentativoLezioneRepository tentativoRepository;
	/** Per mescolare domande e voci; nei test si passa un Random con un seme fisso (vedi il secondo costruttore). */
	private final Random casuale;

	@Autowired
	public PercorsoService(TappaRepository tappaRepository, LezioneRepository lezioneRepository,
			DomandaRepository domandaRepository, TentativoLezioneRepository tentativoRepository) {
		this(tappaRepository, lezioneRepository, domandaRepository, tentativoRepository, new Random());
	}

	PercorsoService(TappaRepository tappaRepository, LezioneRepository lezioneRepository,
			DomandaRepository domandaRepository, TentativoLezioneRepository tentativoRepository, Random casuale) {
		this.tappaRepository = tappaRepository;
		this.lezioneRepository = lezioneRepository;
		this.domandaRepository = domandaRepository;
		this.tentativoRepository = tentativoRepository;
		this.casuale = casuale;
	}

	/** Le tappe con le loro lezioni in ordine e lo stato di ognuna; le tappe senza lezioni attive non compaiono. */
	@Transactional(readOnly = true)
	public List<TappaPercorsoResponse> mappa(UUID utenteId) {
		List<Tappa> tappe = tappaRepository.findAllByOrderByOrdineAsc();
		Map<UUID, List<Lezione>> lezioniPerTappa = lezioniPerTappa();
		Map<UUID, StatoLezione> stati = stati(utenteId, tappe, lezioniPerTappa);

		return tappe.stream()
				.filter(tappa -> lezioniPerTappa.containsKey(tappa.getId()))
				.map(tappa -> TappaPercorsoResponse.da(tappa, lezioniPerTappa.get(tappa.getId()).stream()
						.map(lezione -> LezionePercorsoResponse.da(lezione, stati.get(lezione.getId())))
						.toList()))
				.toList();
	}

	/**
	 * Il contenuto di una lezione da svolgere, senza soluzioni né spiegazioni. Controlli nell'ordine della spec:
	 * 1. lezione inesistente o cancellata → 404 NON_TROVATO
	 * 2. lezione senza domande attive → 404 NON_TROVATO (non si può avviare)
	 * 3. lezione BLOCCATA per l'utente → 403 LEZIONE_BLOCCATA (una COMPLETATA si può rifare)
	 * Domande mescolate a ogni richiesta; voci per tipo come in {@link #domanda}.
	 */
	@Transactional(readOnly = true)
	public LezioneContenutoResponse lezione(UUID utenteId, UUID lezioneId) {
		Lezione lezione = lezioneRepository.findById(lezioneId)
				.orElseThrow(() -> new ApplicazioneException(CodiceErrore.NON_TROVATO, "Lezione non trovata"));
		List<Domanda> domande = domandaRepository.findByLezioneOrderByOrdineAsc(lezione);
		if (domande.isEmpty()) {
			throw new ApplicazioneException(CodiceErrore.NON_TROVATO, "Questa lezione non ha ancora domande");
		}
		StatoLezione stato = stati(utenteId, tappaRepository.findAllByOrderByOrdineAsc(), lezioniPerTappa())
				.get(lezioneId);
		if (stato != StatoLezione.COMPLETATA && stato != StatoLezione.SBLOCCATA) {
			throw new ApplicazioneException(CodiceErrore.LEZIONE_BLOCCATA, "Completa prima le lezioni precedenti");
		}
		List<DomandaLezioneResponse> contenuto = mescolate(domande).stream().map(d -> domanda(d, casuale)).toList();
		return new LezioneContenutoResponse(lezione.getId(), lezione.getTitolo(), lezione.getDescrizione(), contenuto);
	}

	/**
	 * Una domanda per chi studia, solo con id e testi (mai corretta, abbinamentoCorretto o spiegazione):
	 * - Vero o falso: le due opzioni nell'ordine fisso dell'admin (Vero, Falso)
	 * - Scelta multipla: opzioni mescolate
	 * - Abbinamento: sinistra e destra mescolate **ognuna per conto suo**, così la posizione di un elemento a destra
	 *   non dice quale sinistro è il suo compagno
	 * Da chiamare dentro una transazione (opzioni ed elementi sono LAZY).
	 */
	static DomandaLezioneResponse domanda(Domanda domanda, Random casuale) {
		List<VoceResponse> opzioni = domanda.getOpzioni().stream()
				.map(opzione -> new VoceResponse(opzione.getId(), opzione.getTesto()))
				.toList();
		List<VoceResponse> sinistra = voci(domanda, Lato.SINISTRA);
		List<VoceResponse> destra = voci(domanda, Lato.DESTRA);
		if (domanda.getTipo() == TipoDomanda.SCELTA_MULTIPLA) {
			opzioni = mescolate(opzioni, casuale);
		}
		return new DomandaLezioneResponse(domanda.getId(), domanda.getTesto(), domanda.getTipo(), opzioni,
				mescolate(sinistra, casuale), mescolate(destra, casuale));
	}

	private static List<VoceResponse> voci(Domanda domanda, Lato lato) {
		return domanda.getElementi().stream()
				.filter(elemento -> elemento.getLato() == lato)
				.map(elemento -> new VoceResponse(elemento.getId(), elemento.getTesto()))
				.toList();
	}

	private <T> List<T> mescolate(List<T> elenco) {
		return mescolate(elenco, casuale);
	}

	private static <T> List<T> mescolate(List<T> elenco, Random casuale) {
		List<T> copia = new ArrayList<>(elenco);
		Collections.shuffle(copia, casuale);
		return copia;
	}

	/** Lezioni attive raggruppate per tappa, ognuna in ordine. */
	private Map<UUID, List<Lezione>> lezioniPerTappa() {
		return lezioneRepository.findAllByOrderByOrdineAsc().stream()
				.collect(Collectors.groupingBy(lezione -> lezione.getTappa().getId(), LinkedHashMap::new,
						Collectors.toList()));
	}

	/** Lo stato di ogni lezione per l'utente, nell'ordine del percorso (prima la tappa, poi la lezione). */
	private Map<UUID, StatoLezione> stati(UUID utenteId, List<Tappa> tappe, Map<UUID, List<Lezione>> lezioniPerTappa) {
		List<UUID> percorso = new ArrayList<>();
		for (Tappa tappa : tappe) {
			lezioniPerTappa.getOrDefault(tappa.getId(), List.of()).forEach(lezione -> percorso.add(lezione.getId()));
		}
		return calcolaStati(percorso, new HashSet<>(tentativoRepository.lezioniCompletate(utenteId)));
	}

	/**
	 * Stato di ogni lezione, dato l'ordine del percorso e le lezioni completate dall'utente (con un tentativo SUPERATO):
	 * - COMPLETATA se l'utente l'ha completata (anche se nel frattempo è cambiato l'ordine)
	 * - SBLOCCATA se non è completata ma è la prima del percorso o la precedente è completata
	 * - BLOCCATA altrimenti
	 * Lo sblocco è sequenziale su tutto il percorso: l'ultima lezione di una tappa sblocca la prima della successiva.
	 */
	static Map<UUID, StatoLezione> calcolaStati(List<UUID> percorso, Set<UUID> completate) {
		Map<UUID, StatoLezione> stati = new LinkedHashMap<>();
		boolean precedenteCompletata = true;
		for (UUID lezione : percorso) {
			boolean completata = completate.contains(lezione);
			stati.put(lezione, completata ? StatoLezione.COMPLETATA
					: precedenteCompletata ? StatoLezione.SBLOCCATA : StatoLezione.BLOCCATA);
			precedenteCompletata = completata;
		}
		return stati;
	}

}
