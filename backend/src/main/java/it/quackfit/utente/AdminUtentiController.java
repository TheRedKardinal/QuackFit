package it.quackfit.utente;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import it.quackfit.common.PaginaResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Gestione degli utenti (vedi admin.md, Utenti). Solo ADMIN e SUPERADMIN con email verificata (regola in SecurityConfig).
 * page e size fuori limite → 400 VALIDAZIONE con il parametro in `campi` (validazione dei parametri di Spring);
 * non numerici → lo stesso 400 (ExceptionsHandler); se mancano valgono 0 e 20.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUtentiController {

	private final AdminUtentiService adminUtentiService;

	public AdminUtentiController(AdminUtentiService adminUtentiService) {
		this.adminUtentiService = adminUtentiService;
	}

	@GetMapping
	public PaginaResponse<UtenteAdminResponse> lista(
			@RequestParam(name = "page", defaultValue = "0") @Min(value = 0, message = "La pagina non può essere negativa") int page,
			@RequestParam(name = "size", defaultValue = "20")
			@Min(value = 1, message = "La dimensione deve essere tra 1 e 50")
			@Max(value = 50, message = "La dimensione deve essere tra 1 e 50") int size,
			@RequestParam(name = "q", required = false) String q) {
		return adminUtentiService.lista(page, size, q);
	}

	@PatchMapping("/{id}/role")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cambiaRuolo(@AuthenticationPrincipal Utente chiamante, @PathVariable("id") UUID id,
			@Valid @RequestBody CambioRuoloRequest richiesta) {
		adminUtentiService.cambiaRuolo(chiamante, id, richiesta.ruolo());
	}

}
