package it.quackfit.contenuti;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body di POST /api/admin/lessons/{lessonId}/questions (vedi admin.md, Domande). @Valid controlla solo i testi e le
 * lunghezze; le regole del tipo (campi presenti, numero di opzioni o coppie, una sola corretta, testi ripetuti) le
 * controlla il service con {@link RegoleDomanda#creazione}.
 */
public record CreazioneDomandaRequest(

		@NotBlank(message = "Il testo è obbligatorio")
		@Size(max = Domanda.MAX_TESTO, message = "Massimo " + Domanda.MAX_TESTO + " caratteri")
		String testo,

		@NotBlank(message = "La spiegazione è obbligatoria")
		@Size(max = Domanda.MAX_SPIEGAZIONE, message = "Massimo " + Domanda.MAX_SPIEGAZIONE + " caratteri")
		String spiegazione,

		@NotNull(message = "Il tipo è obbligatorio")
		TipoDomanda tipo,

		/** Solo VERO_FALSO: true se la risposta giusta è "Vero". */
		Boolean rispostaVero,

		/** Solo SCELTA_MULTIPLA. */
		List<@NotNull(message = "L'opzione è obbligatoria") @Valid OpzioneRequest> opzioni,

		/** Solo ABBINAMENTO. */
		List<@NotNull(message = "La coppia è obbligatoria") @Valid CoppiaRequest> coppie) {

	/** Pulizia dell'input: strip() sui testi. */
	public CreazioneDomandaRequest {
		testo = testo == null ? null : testo.strip();
		spiegazione = spiegazione == null ? null : spiegazione.strip();
	}

	/** Un'opzione di SCELTA_MULTIPLA; `corretta` assente vale false. */
	public record OpzioneRequest(

			@NotBlank(message = "Il testo è obbligatorio")
			@Size(max = Opzione.MAX_TESTO, message = "Massimo " + Opzione.MAX_TESTO + " caratteri")
			String testo,

			Boolean corretta) {

		/** Boolean e non boolean: Jackson 3 rifiuta un primitivo assente; assente vale false. */
		public OpzioneRequest {
			testo = testo == null ? null : testo.strip();
			corretta = corretta != null && corretta;
		}

	}

	/** Una coppia di ABBINAMENTO: il backend crea i due elementi e il collegamento giusto. */
	public record CoppiaRequest(

			@NotBlank(message = "Il testo è obbligatorio")
			@Size(max = ElementoAbbinamento.MAX_TESTO, message = "Massimo " + ElementoAbbinamento.MAX_TESTO + " caratteri")
			String sinistra,

			@NotBlank(message = "Il testo è obbligatorio")
			@Size(max = ElementoAbbinamento.MAX_TESTO, message = "Massimo " + ElementoAbbinamento.MAX_TESTO + " caratteri")
			String destra) {

		public CoppiaRequest {
			sinistra = sinistra == null ? null : sinistra.strip();
			destra = destra == null ? null : destra.strip();
		}

	}

}
