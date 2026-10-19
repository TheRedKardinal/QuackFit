package it.quackfit.tentativi;

import java.util.List;
import java.util.UUID;

/**
 * Risposta di POST /api/attempts/{id}/answers (tentativi.md, Risposta del server): l'esito, la soluzione e la
 * spiegazione arrivano solo qui, dopo l'invio, mai nel contenuto della lezione.
 */
public record EsitoRispostaResponse(boolean corretta, SoluzioneResponse soluzione, String spiegazione, int errori,
		EsitoTentativo esito) {

	/**
	 * La soluzione, con entrambi i campi sempre presenti (deviazione di forma dalla spec, stesso significato):
	 * VERO_FALSO e SCELTA_MULTIPLA → `opzioneId` dell'opzione giusta e `coppie` = [];
	 * ABBINAMENTO → `opzioneId` = null e `coppie` con le coppie giuste.
	 */
	public record SoluzioneResponse(UUID opzioneId, List<CoppiaSoluzioneResponse> coppie) {
	}

	public record CoppiaSoluzioneResponse(UUID sinistroId, UUID destroId) {
	}

}
