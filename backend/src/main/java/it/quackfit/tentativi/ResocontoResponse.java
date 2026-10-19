package it.quackfit.tentativi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import it.quackfit.contenuti.TipoDomanda;

/**
 * Resoconto di un tentativo (tentativi.md, Resoconto): solo le domande già risposte, nell'ordine dell'admin, con la
 * prima risposta data. I testi viaggiano con gli id perché domande, opzioni ed elementi possono essere cancellati dopo
 * il tentativo. Come nelle risposte, tutti i campi ci sono sempre: `risposta` e `soluzione` sono null per
 * l'abbinamento, `coppie` è vuota per le domande a opzioni.
 *
 * @param finitoAt null se il tentativo è in corso
 */
public record ResocontoResponse(UUID id, EsitoTentativo esito, int errori, Instant iniziatoAt, Instant finitoAt,
		LezioneResponse lezione, List<DomandaResponse> domande) {

	public record LezioneResponse(UUID id, String titolo) {
	}

	/** Un'opzione o un elemento di un abbinamento. */
	public record VoceResponse(UUID id, String testo) {
	}

	/**
	 * @param corretta  la prima risposta era giusta (per l'abbinamento: tutte le coppie giuste)
	 * @param risposta  l'opzione scelta con la prima risposta (null per l'abbinamento)
	 * @param soluzione l'opzione giusta (null per l'abbinamento)
	 */
	public record DomandaResponse(UUID id, String testo, TipoDomanda tipo, String spiegazione, boolean corretta,
			VoceResponse risposta, VoceResponse soluzione, List<CoppiaResponse> coppie) {
	}

	/** Una coppia della prima risposta, con il destro giusto per quel sinistro. */
	public record CoppiaResponse(VoceResponse sinistro, VoceResponse destro, VoceResponse soluzione, boolean corretta) {
	}

	public static ResocontoResponse da(IntestazioneResoconto intestazione, List<DomandaResponse> domande) {
		return new ResocontoResponse(intestazione.tentativoId(), intestazione.esito(), intestazione.errori(),
				intestazione.iniziatoAt(), intestazione.finitoAt(),
				new LezioneResponse(intestazione.lezioneId(), intestazione.lezioneTitolo()), domande);
	}

}
