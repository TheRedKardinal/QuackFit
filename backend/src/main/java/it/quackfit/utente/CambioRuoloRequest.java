package it.quackfit.utente;

import jakarta.validation.constraints.NotNull;

/**
 * Body di PATCH /api/admin/users/{id}/role. Un valore che non è un ruolo (es. "PIPPO") non si legge e risponde
 * 400 VALIDAZIONE come ogni corpo illeggibile; SUPERADMIN si legge ma il service lo rifiuta (403).
 */
public record CambioRuoloRequest(

		@NotNull(message = "Il ruolo è obbligatorio")
		Ruolo ruolo) {
}
