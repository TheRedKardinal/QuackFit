package it.quackfit.contenuti;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ObjIntConsumer;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;

/**
 * Ordine di tappe e lezioni (vedi admin.md, Ordine): gli elementi attivi sono sempre 1, 2, 3… senza buchi.
 * Lavora sulla lista già letta (e bloccata) dal service, in ordine; cambia solo il campo ordine delle entità.
 */
final class Ordinamento {

	private Ordinamento() {
	}

	/**
	 * Porta l'elemento alla posizione indicata (da 1) e fa scalare quelli in mezzo. Posizione fuori da 1…n → 400
	 * VALIDAZIONE con il campo `ordine`.
	 */
	static <T> void sposta(List<T> elementi, T elemento, int posizione, ObjIntConsumer<T> impostaOrdine) {
		if (posizione < 1 || posizione > elementi.size()) {
			throw new ApplicazioneException(CodiceErrore.VALIDAZIONE, "Uno o più campi non sono validi",
					Map.of("ordine", "La posizione deve essere tra 1 e " + elementi.size()));
		}
		List<T> nuovoOrdine = new ArrayList<>(elementi);
		nuovoOrdine.remove(elemento);
		nuovoOrdine.add(posizione - 1, elemento);
		rinumera(nuovoOrdine, impostaOrdine);
	}

	/** Assegna 1, 2, 3… nell'ordine della lista (dopo una cancellazione chiude il buco). */
	static <T> void rinumera(List<T> elementi, ObjIntConsumer<T> impostaOrdine) {
		for (int i = 0; i < elementi.size(); i++) {
			impostaOrdine.accept(elementi.get(i), i + 1);
		}
	}

}
