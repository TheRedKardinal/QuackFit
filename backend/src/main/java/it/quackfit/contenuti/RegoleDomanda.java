package it.quackfit.contenuti;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import it.quackfit.contenuti.CreazioneDomandaRequest.CoppiaRequest;
import it.quackfit.contenuti.CreazioneDomandaRequest.OpzioneRequest;
import it.quackfit.contenuti.ModificaDomandaRequest.TestoRequest;

/**
 * Regole delle domande che @Valid non può controllare (vedi admin.md, Domande): dipendono dal tipo o dalla domanda
 * salvata. Tutte qui, senza Spring: restituiscono gli errori per campo (vuoti = tutto valido) e il service li
 * trasforma in 400 VALIDAZIONE. Si chiamano dopo @Valid, quindi i testi non sono mai null né vuoti.
 *
 * Testi ripetuti: dentro la stessa lista (opzioni; elementi di sinistra; elementi di destra), dopo strip() e senza
 * distinguere maiuscole e minuscole. Lo stesso testo può stare a sinistra e a destra. L'errore va sul secondo.
 */
final class RegoleDomanda {

	static final int MIN_ELEMENTI = 2;
	static final int MAX_ELEMENTI = 6;

	static final String MSG_RISPOSTA_VERO = "Indica se la risposta giusta è Vero o Falso";
	static final String MSG_NON_PREVISTO = "Non previsto per questo tipo di domanda";
	static final String MSG_NUMERO_OPZIONI = "Servono da " + MIN_ELEMENTI + " a " + MAX_ELEMENTI + " opzioni";
	static final String MSG_UNA_CORRETTA = "Una sola opzione deve essere corretta";
	static final String MSG_NUMERO_COPPIE = "Servono da " + MIN_ELEMENTI + " a " + MAX_ELEMENTI + " coppie";
	static final String MSG_OPZIONE_UGUALE = "Questa opzione è uguale a un'altra";
	static final String MSG_SINISTRA_UGUALE = "Questo elemento è uguale a un altro della colonna di sinistra";
	static final String MSG_DESTRA_UGUALE = "Questo elemento è uguale a un altro della colonna di destra";
	static final String MSG_VERO_FALSO_FISSI = "I testi di Vero e Falso non si modificano";
	static final String MSG_ID_ESTRANEO = "Non appartiene a questa domanda";
	static final String MSG_ID_RIPETUTO = "Indicato più di una volta";

	private RegoleDomanda() {
	}

	// --- creazione ---

	/** Regole del tipo e testi ripetuti di una nuova domanda. */
	static Map<String, String> creazione(CreazioneDomandaRequest richiesta) {
		Map<String, String> campi = new LinkedHashMap<>();
		switch (richiesta.tipo()) {
			case VERO_FALSO -> {
				if (richiesta.rispostaVero() == null) {
					campi.put("rispostaVero", MSG_RISPOSTA_VERO);
				}
				nonPrevisto(campi, "opzioni", richiesta.opzioni());
				nonPrevisto(campi, "coppie", richiesta.coppie());
			}
			case SCELTA_MULTIPLA -> {
				nonPrevisto(campi, "rispostaVero", richiesta.rispostaVero());
				nonPrevisto(campi, "coppie", richiesta.coppie());
				List<OpzioneRequest> opzioni = richiesta.opzioni();
				if (opzioni == null || opzioni.size() < MIN_ELEMENTI || opzioni.size() > MAX_ELEMENTI) {
					campi.put("opzioni", MSG_NUMERO_OPZIONI);
				} else {
					if (opzioni.stream().filter(OpzioneRequest::corretta).count() != 1) {
						campi.put("opzioni", MSG_UNA_CORRETTA);
					}
					ripetuti(campi, opzioni.stream().map(OpzioneRequest::testo).toList(),
							i -> "opzioni[" + i + "].testo", MSG_OPZIONE_UGUALE);
				}
			}
			case ABBINAMENTO -> {
				nonPrevisto(campi, "rispostaVero", richiesta.rispostaVero());
				nonPrevisto(campi, "opzioni", richiesta.opzioni());
				List<CoppiaRequest> coppie = richiesta.coppie();
				if (coppie == null || coppie.size() < MIN_ELEMENTI || coppie.size() > MAX_ELEMENTI) {
					campi.put("coppie", MSG_NUMERO_COPPIE);
				} else {
					ripetuti(campi, coppie.stream().map(CoppiaRequest::sinistra).toList(),
							i -> "coppie[" + i + "].sinistra", MSG_SINISTRA_UGUALE);
					ripetuti(campi, coppie.stream().map(CoppiaRequest::destra).toList(),
							i -> "coppie[" + i + "].destra", MSG_DESTRA_UGUALE);
				}
			}
		}
		return campi;
	}

	private static void nonPrevisto(Map<String, String> campi, String campo, Object valore) {
		if (valore != null) {
			campi.put(campo, MSG_NON_PREVISTO);
		}
	}

	/** Segna con il messaggio ogni testo uguale a uno precedente della stessa lista. */
	private static void ripetuti(Map<String, String> campi, List<String> testi, Function<Integer, String> chiave,
			String messaggio) {
		Set<String> visti = new HashSet<>();
		for (int i = 0; i < testi.size(); i++) {
			if (!visti.add(normalizza(testi.get(i)))) {
				campi.putIfAbsent(chiave.apply(i), messaggio);
			}
		}
	}

	private static String normalizza(String testo) {
		return testo.strip().toLowerCase(Locale.ROOT);
	}

	// --- modifica ---

	/**
	 * Regole della modifica dei testi: liste ammesse solo per il loro tipo, id che appartengono alla domanda e non
	 * ripetuti, testi ripetuti controllati sul risultato finale (testi nuovi al posto dei vecchi). Un testo ripetuto
	 * si segnala sul campo della richiesta che lo ha reso uguale a un altro.
	 */
	static Map<String, String> modifica(Domanda domanda, ModificaDomandaRequest richiesta) {
		Map<String, String> campi = new LinkedHashMap<>();
		if (richiesta.opzioni() != null) {
			switch (domanda.getTipo()) {
				case VERO_FALSO -> campi.put("opzioni", MSG_VERO_FALSO_FISSI);
				case ABBINAMENTO -> campi.put("opzioni", MSG_NON_PREVISTO);
				case SCELTA_MULTIPLA -> idValidi(campi, "opzioni", richiesta.opzioni(),
						domanda.getOpzioni().stream().map(Opzione::getId).toList());
			}
		}
		if (richiesta.elementi() != null) {
			if (domanda.getTipo() != TipoDomanda.ABBINAMENTO) {
				campi.put("elementi", MSG_NON_PREVISTO);
			} else {
				idValidi(campi, "elementi", richiesta.elementi(),
						domanda.getElementi().stream().map(ElementoAbbinamento::getId).toList());
			}
		}
		if (!campi.isEmpty()) {
			return campi;
		}
		if (richiesta.opzioni() != null) {
			ripetutiDopoModifica(campi, "opzioni", richiesta.opzioni(),
					domanda.getOpzioni().stream().map(o -> new Attuale(o.getId(), o.getTesto())).toList(),
					MSG_OPZIONE_UGUALE);
		}
		if (richiesta.elementi() != null) {
			for (Lato lato : Lato.values()) {
				List<Attuale> colonna = domanda.getElementi().stream()
						.filter(e -> e.getLato() == lato)
						.map(e -> new Attuale(e.getId(), e.getTesto()))
						.toList();
				ripetutiDopoModifica(campi, "elementi", richiesta.elementi(), colonna,
						lato == Lato.SINISTRA ? MSG_SINISTRA_UGUALE : MSG_DESTRA_UGUALE);
			}
		}
		return campi;
	}

	/** Un'opzione o un elemento salvato, con il testo di adesso. */
	private record Attuale(UUID id, String testo) {
	}

	private static void idValidi(Map<String, String> campi, String lista, List<TestoRequest> richieste,
			List<UUID> idDellaDomanda) {
		Set<UUID> visti = new HashSet<>();
		for (int i = 0; i < richieste.size(); i++) {
			UUID id = richieste.get(i).id();
			if (!idDellaDomanda.contains(id)) {
				campi.putIfAbsent(lista + "[" + i + "].id", MSG_ID_ESTRANEO);
			} else if (!visti.add(id)) {
				campi.putIfAbsent(lista + "[" + i + "].id", MSG_ID_RIPETUTO);
			}
		}
	}

	/**
	 * Testi ripetuti nella lista finale (nell'ordine salvato). Per ogni coppia di testi uguali l'errore va sul secondo
	 * se la richiesta lo modifica, altrimenti sul primo (almeno uno dei due l'ha toccato la richiesta, perché prima
	 * della modifica non c'erano testi ripetuti).
	 */
	private static void ripetutiDopoModifica(Map<String, String> campi, String lista, List<TestoRequest> richieste,
			List<Attuale> attuali, String messaggio) {
		Map<UUID, Integer> posizioneNellaRichiesta = new HashMap<>();
		for (int i = 0; i < richieste.size(); i++) {
			posizioneNellaRichiesta.put(richieste.get(i).id(), i);
		}
		List<UUID> ids = new ArrayList<>();
		List<String> testiFinali = new ArrayList<>();
		for (Attuale attuale : attuali) {
			Integer posizione = posizioneNellaRichiesta.get(attuale.id());
			ids.add(attuale.id());
			testiFinali.add(posizione == null ? attuale.testo() : richieste.get(posizione).testo());
		}
		Map<String, UUID> primoConTesto = new HashMap<>();
		for (int i = 0; i < testiFinali.size(); i++) {
			UUID primo = primoConTesto.putIfAbsent(normalizza(testiFinali.get(i)), ids.get(i));
			if (primo != null) {
				Integer posizione = posizioneNellaRichiesta.containsKey(ids.get(i))
						? posizioneNellaRichiesta.get(ids.get(i))
						: posizioneNellaRichiesta.get(primo);
				if (posizione != null) {
					campi.putIfAbsent(lista + "[" + posizione + "].testo", messaggio);
				}
			}
		}
	}

}
