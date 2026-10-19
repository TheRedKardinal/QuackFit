package it.quackfit.contenuti;

import java.util.List;
import java.util.UUID;

/**
 * GET /api/path/lessons/{id} (percorso.md, Contenuto di una lezione): la lezione con le domande per chi studia.
 * Record separati da quelli dell'admin: qui non esistono campi con la soluzione (corretta, rispostaVero,
 * abbinamentoCorretto) né la spiegazione, che arrivano solo dopo aver inviato una risposta (tentativi.md).
 */
public record LezioneContenutoResponse(UUID id, String titolo, String descrizione, List<DomandaLezioneResponse> domande) {

	/**
	 * Una domanda: `opzioni` (Vero o falso, Scelta multipla), `sinistra` e `destra` (Abbinamento) ci sono sempre;
	 * quelli che non riguardano il tipo sono [].
	 */
	public record DomandaLezioneResponse(UUID id, String testo, TipoDomanda tipo, List<VoceResponse> opzioni,
			List<VoceResponse> sinistra, List<VoceResponse> destra) {
	}

	/** Un'opzione o un elemento dell'abbinamento: solo id e testo. */
	public record VoceResponse(UUID id, String testo) {
	}

}
