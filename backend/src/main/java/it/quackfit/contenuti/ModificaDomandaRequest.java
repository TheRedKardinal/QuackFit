package it.quackfit.contenuti;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body di PATCH /api/admin/questions/{questionId}: solo i testi (vedi admin.md, Domande). Ogni campo è facoltativo,
 * assente o null = invariato; un testo presente non può essere vuoto. I campi di struttura (tipo, corretta,
 * rispostaVero, coppie) non esistono qui: se arrivano, Jackson li ignora e la struttura resta invariata.
 * Le regole che dipendono dalla domanda (id che le appartengono, tipo, testi ripetuti) le controlla il service con
 * {@link RegoleDomanda#modifica}.
 */
public record ModificaDomandaRequest(

		@Pattern(regexp = "(?s).+", message = "Il testo non può essere vuoto")
		@Size(max = Domanda.MAX_TESTO, message = "Massimo " + Domanda.MAX_TESTO + " caratteri")
		String testo,

		@Pattern(regexp = "(?s).+", message = "La spiegazione non può essere vuota")
		@Size(max = Domanda.MAX_SPIEGAZIONE, message = "Massimo " + Domanda.MAX_SPIEGAZIONE + " caratteri")
		String spiegazione,

		/** Solo SCELTA_MULTIPLA: nuovi testi di alcune opzioni; quelle non incluse restano invariate. */
		List<@NotNull(message = "L'opzione è obbligatoria") @Valid TestoRequest> opzioni,

		/** Solo ABBINAMENTO: nuovi testi di alcuni elementi (sinistra o destra); gli altri restano invariati. */
		List<@NotNull(message = "L'elemento è obbligatorio") @Valid TestoRequest> elementi) {

	/** Pulizia dell'input: strip() sui testi. */
	public ModificaDomandaRequest {
		testo = testo == null ? null : testo.strip();
		spiegazione = spiegazione == null ? null : spiegazione.strip();
	}

	/** Nuovo testo di un'opzione o di un elemento esistente. Opzioni ed elementi hanno lo stesso limite. */
	public record TestoRequest(

			@NotNull(message = "L'id è obbligatorio")
			UUID id,

			@NotBlank(message = "Il testo è obbligatorio")
			@Size(max = Opzione.MAX_TESTO, message = "Massimo " + Opzione.MAX_TESTO + " caratteri")
			String testo) {

		public TestoRequest {
			testo = testo == null ? null : testo.strip();
		}

	}

}
