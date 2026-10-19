package it.quackfit.contenuti;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.tentativi.TentativoLezioneRepository;

/**
 * Mappa del percorso per l'utente loggato (percorso.md, Stato delle lezioni e Mappa). Contano solo tappe e lezioni
 * attive (@SQLRestriction); lo stato non è salvato, si calcola a ogni richiesta, così se un admin inserisce, sposta o
 * cancella una lezione il percorso si ricalcola da solo.
 */
@Service
public class PercorsoService {

	private final TappaRepository tappaRepository;
	private final LezioneRepository lezioneRepository;
	private final TentativoLezioneRepository tentativoRepository;

	public PercorsoService(TappaRepository tappaRepository, LezioneRepository lezioneRepository,
			TentativoLezioneRepository tentativoRepository) {
		this.tappaRepository = tappaRepository;
		this.lezioneRepository = lezioneRepository;
		this.tentativoRepository = tentativoRepository;
	}

	/** Le tappe con le loro lezioni in ordine e lo stato di ognuna; le tappe senza lezioni attive non compaiono. */
	@Transactional(readOnly = true)
	public List<TappaPercorsoResponse> mappa(UUID utenteId) {
		List<Tappa> tappe = tappaRepository.findAllByOrderByOrdineAsc();
		Map<UUID, List<Lezione>> lezioniPerTappa = lezioneRepository.findAllByOrderByOrdineAsc().stream()
				.collect(Collectors.groupingBy(lezione -> lezione.getTappa().getId(), LinkedHashMap::new,
						Collectors.toList()));
		// tutte le lezioni nell'ordine del percorso: prima l'ordine della tappa, poi quello della lezione
		List<UUID> percorso = new ArrayList<>();
		for (Tappa tappa : tappe) {
			lezioniPerTappa.getOrDefault(tappa.getId(), List.of()).forEach(lezione -> percorso.add(lezione.getId()));
		}
		Map<UUID, StatoLezione> stati = calcolaStati(percorso,
				new HashSet<>(tentativoRepository.lezioniCompletate(utenteId)));

		return tappe.stream()
				.filter(tappa -> lezioniPerTappa.containsKey(tappa.getId()))
				.map(tappa -> TappaPercorsoResponse.da(tappa, lezioniPerTappa.get(tappa.getId()).stream()
						.map(lezione -> LezionePercorsoResponse.da(lezione, stati.get(lezione.getId())))
						.toList()))
				.toList();
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
