package it.quackfit.contenuti;

import java.util.List;
import java.util.UUID;

/**
 * Domanda nella vista admin, con le soluzioni (vedi admin.md, Formato di una domanda). Le due liste ci sono sempre:
 * `opzioni` è vuota per ABBINAMENTO, `coppie` è vuota per VERO_FALSO e SCELTA_MULTIPLA.
 */
public record DomandaAdminResponse(UUID id, String testo, String spiegazione, TipoDomanda tipo, int ordine,
		List<OpzioneAdminResponse> opzioni, List<CoppiaAdminResponse> coppie) {

	public record OpzioneAdminResponse(UUID id, String testo, boolean corretta) {
	}

	public record ElementoAdminResponse(UUID id, String testo) {

		static ElementoAdminResponse da(ElementoAbbinamento elemento) {
			return new ElementoAdminResponse(elemento.getId(), elemento.getTesto());
		}

	}

	public record CoppiaAdminResponse(ElementoAdminResponse sinistro, ElementoAdminResponse destro) {
	}

	/** Da chiamare dentro una transazione: legge opzioni ed elementi della domanda (LAZY). */
	public static DomandaAdminResponse da(Domanda domanda) {
		List<OpzioneAdminResponse> opzioni = domanda.getOpzioni().stream()
				.map(opzione -> new OpzioneAdminResponse(opzione.getId(), opzione.getTesto(), opzione.isCorretta()))
				.toList();
		// Gli elementi sono ordinati per lato e ordine: i SINISTRA, filtrati, sono già nell'ordine delle coppie
		List<CoppiaAdminResponse> coppie = domanda.getElementi().stream()
				.filter(elemento -> elemento.getLato() == Lato.SINISTRA)
				.map(sinistro -> new CoppiaAdminResponse(ElementoAdminResponse.da(sinistro),
						ElementoAdminResponse.da(sinistro.getAbbinamentoCorretto())))
				.toList();
		return new DomandaAdminResponse(domanda.getId(), domanda.getTesto(), domanda.getSpiegazione(),
				domanda.getTipo(), domanda.getOrdine(), opzioni, coppie);
	}

}
