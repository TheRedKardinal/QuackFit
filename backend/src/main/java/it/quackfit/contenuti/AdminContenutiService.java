package it.quackfit.contenuti;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;

/**
 * Gestione di tappe e lezioni da parte degli admin (vedi admin.md, Ordine, Tappe e Lezioni).
 *
 * Ogni operazione che cambia l'ordine (creazione, riordino, cancellazione) blocca prima le righe da rinumerare con un
 * lock pessimistico, così due admin in contemporanea non lasciano ordini duplicati o buchi:
 * - tappe: tutte le tappe, sempre per ordine e id ({@link TappaRepository#bloccaTutte})
 * - lezioni: la tappa genitore ({@link TappaRepository#blocca}), che esiste anche quando non ha lezioni
 * Le righe si leggono DOPO il lock, così hanno già i valori scritti dalle transazioni precedenti.
 */
@Service
public class AdminContenutiService {

	private final TappaRepository tappaRepository;
	private final LezioneRepository lezioneRepository;
	private final DomandaRepository domandaRepository;
	private final Clock clock;

	public AdminContenutiService(TappaRepository tappaRepository, LezioneRepository lezioneRepository,
			DomandaRepository domandaRepository, Clock clock) {
		this.tappaRepository = tappaRepository;
		this.lezioneRepository = lezioneRepository;
		this.domandaRepository = domandaRepository;
		this.clock = clock;
	}

	// --- tappe ---

	/** Tutte le tappe attive in ordine, ognuna con le sue lezioni attive in ordine e il numero di domande. */
	@Transactional(readOnly = true)
	public List<TappaAdminResponse> elenco() {
		Map<UUID, Long> domande = numeroDomandePerLezione();
		Map<UUID, List<LezioneAdminResponse>> lezioniPerTappa = lezioneRepository.findAllByOrderByOrdineAsc().stream()
				.collect(Collectors.groupingBy(lezione -> lezione.getTappa().getId(),
						Collectors.mapping(lezione -> LezioneAdminResponse.da(lezione,
								domande.getOrDefault(lezione.getId(), 0L)), Collectors.toList())));
		return tappaRepository.findAllByOrderByOrdineAsc().stream()
				.map(tappa -> TappaAdminResponse.da(tappa, lezioniPerTappa.getOrDefault(tappa.getId(), List.of())))
				.toList();
	}

	/** Nuova tappa in fondo (max + 1), senza lezioni. */
	@Transactional
	public TappaAdminResponse creaTappa(CreazioneTappaRequest richiesta) {
		tappaRepository.bloccaTutte();
		Tappa tappa = new Tappa(richiesta.titolo(), richiesta.descrizione(), tappaRepository.ordineMassimo() + 1,
				Instant.now(clock));
		return TappaAdminResponse.da(tappaRepository.save(tappa), List.of());
	}

	/** Modifica parziale: i campi null restano invariati; con `ordine` la tappa si sposta e le altre scalano. */
	@Transactional
	public TappaAdminResponse modificaTappa(UUID tappaId, ModificaTappaRequest richiesta) {
		Tappa tappa;
		if (richiesta.ordine() != null) {
			List<Tappa> tappe = tappaRepository.bloccaTutte();
			tappa = trova(tappe, tappaId, Tappa::getId, "Tappa non trovata");
			Ordinamento.sposta(tappe, tappa, richiesta.ordine(), Tappa::setOrdine);
		} else {
			tappa = tappaRepository.findById(tappaId).orElseThrow(() -> nonTrovato("Tappa non trovata"));
		}
		if (richiesta.titolo() != null) {
			tappa.setTitolo(richiesta.titolo());
		}
		if (richiesta.descrizione() != null) {
			tappa.setDescrizione(richiesta.descrizione());
		}
		Map<UUID, Long> domande = numeroDomandePerLezione();
		List<LezioneAdminResponse> lezioni = lezioneRepository.findByTappaOrderByOrdineAsc(tappa).stream()
				.map(lezione -> LezioneAdminResponse.da(lezione, domande.getOrDefault(lezione.getId(), 0L)))
				.toList();
		return TappaAdminResponse.da(tappa, lezioni);
	}

	/**
	 * Soft delete della tappa e, a cascata, delle sue lezioni e domande, con lo stesso istante; le tappe rimaste si
	 * rinumerano. La rinumerazione si fa per prima: le UPDATE della cascata scrivono prima le modifiche in sospeso
	 * (flushAutomatically) e poi svuotano il contesto di persistenza (clearAutomatically).
	 * Ordine della cascata: domande, lezioni, tappa (la query delle domande raggiunge le lezioni ancora attive).
	 */
	@Transactional
	public void cancellaTappa(UUID tappaId) {
		List<Tappa> tappe = tappaRepository.bloccaTutte();
		Tappa tappa = trova(tappe, tappaId, Tappa::getId, "Tappa non trovata");
		List<Tappa> rimaste = new ArrayList<>(tappe);
		rimaste.remove(tappa);
		Ordinamento.rinumera(rimaste, Tappa::setOrdine);
		Instant adesso = Instant.now(clock);
		domandaRepository.cancellaDellaTappa(tappaId, adesso);
		lezioneRepository.cancellaDellaTappa(tappaId, adesso);
		tappaRepository.cancella(tappaId, adesso);
	}

	// --- lezioni ---

	/** Nuova lezione in fondo alla tappa (max + 1), senza domande. Tappa inesistente o cancellata → 404. */
	@Transactional
	public LezioneAdminResponse creaLezione(UUID tappaId, CreazioneLezioneRequest richiesta) {
		Tappa tappa = tappaRepository.blocca(tappaId).orElseThrow(() -> nonTrovato("Tappa non trovata"));
		Lezione lezione = new Lezione(tappa, richiesta.titolo(), richiesta.descrizione(),
				lezioneRepository.ordineMassimo(tappa) + 1, Instant.now(clock));
		return LezioneAdminResponse.da(lezioneRepository.save(lezione), 0);
	}

	/** La lezione con tutte le sue domande attive in ordine, complete di soluzioni. */
	@Transactional(readOnly = true)
	public LezioneDettaglioAdminResponse dettaglioLezione(UUID lezioneId) {
		Lezione lezione = lezioneRepository.findById(lezioneId).orElseThrow(() -> nonTrovato("Lezione non trovata"));
		List<DomandaAdminResponse> domande = domandaRepository.findByLezioneOrderByOrdineAsc(lezione).stream()
				.map(DomandaAdminResponse::da)
				.toList();
		return LezioneDettaglioAdminResponse.da(lezione, domande);
	}

	/** Modifica parziale; con `ordine` la lezione si sposta dentro la sua tappa e le altre scalano. */
	@Transactional
	public LezioneAdminResponse modificaLezione(UUID lezioneId, ModificaLezioneRequest richiesta) {
		Lezione lezione;
		if (richiesta.ordine() != null) {
			List<Lezione> lezioni = bloccaLezioniDellaTappa(lezioneId);
			lezione = trova(lezioni, lezioneId, Lezione::getId, "Lezione non trovata");
			Ordinamento.sposta(lezioni, lezione, richiesta.ordine(), Lezione::setOrdine);
		} else {
			lezione = lezioneRepository.findById(lezioneId).orElseThrow(() -> nonTrovato("Lezione non trovata"));
		}
		if (richiesta.titolo() != null) {
			lezione.setTitolo(richiesta.titolo());
		}
		if (richiesta.descrizione() != null) {
			lezione.setDescrizione(richiesta.descrizione());
		}
		return LezioneAdminResponse.da(lezione, domandaRepository.countByLezione(lezione));
	}

	/**
	 * Soft delete della lezione e, a cascata, delle sue domande, con lo stesso istante; le lezioni rimaste nella tappa
	 * si rinumerano (prima della cascata, come per le tappe).
	 */
	@Transactional
	public void cancellaLezione(UUID lezioneId) {
		List<Lezione> lezioni = bloccaLezioniDellaTappa(lezioneId);
		Lezione lezione = trova(lezioni, lezioneId, Lezione::getId, "Lezione non trovata");
		List<Lezione> rimaste = new ArrayList<>(lezioni);
		rimaste.remove(lezione);
		Ordinamento.rinumera(rimaste, Lezione::setOrdine);
		Instant adesso = Instant.now(clock);
		domandaRepository.cancellaDellaLezione(lezioneId, adesso);
		lezioneRepository.cancella(lezioneId, adesso);
	}

	// --- supporto ---

	/**
	 * Blocca la tappa della lezione e restituisce le sue lezioni attive in ordine, lette dopo il lock. L'id della tappa
	 * si legge senza caricare la lezione, che altrimenti resterebbe nel contesto con i valori di prima del lock.
	 */
	private List<Lezione> bloccaLezioniDellaTappa(UUID lezioneId) {
		UUID tappaId = lezioneRepository.tappaIdDi(lezioneId).orElseThrow(() -> nonTrovato("Lezione non trovata"));
		// Una tappa cancellata nel frattempo ha cancellato anche la lezione
		Tappa tappa = tappaRepository.blocca(tappaId).orElseThrow(() -> nonTrovato("Lezione non trovata"));
		return lezioneRepository.findByTappaOrderByOrdineAsc(tappa);
	}

	private Map<UUID, Long> numeroDomandePerLezione() {
		return domandaRepository.contaPerLezione().stream()
				.collect(Collectors.toMap(ConteggioDomande::lezioneId, ConteggioDomande::numero));
	}

	/** Cerca l'elemento nella lista già bloccata: se non c'è, è inesistente o cancellato (anche nel frattempo). */
	private static <T> T trova(List<T> elementi, UUID id, Function<T, UUID> idDi, String messaggio) {
		return elementi.stream()
				.filter(elemento -> idDi.apply(elemento).equals(id))
				.findFirst()
				.orElseThrow(() -> nonTrovato(messaggio));
	}

	private static ApplicazioneException nonTrovato(String messaggio) {
		return new ApplicazioneException(CodiceErrore.NON_TROVATO, messaggio);
	}

}
