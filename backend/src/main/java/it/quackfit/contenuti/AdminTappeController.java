package it.quackfit.contenuti;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Gestione delle tappe e creazione delle lezioni di una tappa (vedi admin.md, Tappe e Lezioni). Solo ADMIN e
 * SUPERADMIN con email verificata (regola in SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/stages")
public class AdminTappeController {

	private final AdminContenutiService adminContenutiService;

	public AdminTappeController(AdminContenutiService adminContenutiService) {
		this.adminContenutiService = adminContenutiService;
	}

	@GetMapping
	public List<TappaAdminResponse> elenco() {
		return adminContenutiService.elenco();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TappaAdminResponse crea(@Valid @RequestBody CreazioneTappaRequest richiesta) {
		return adminContenutiService.creaTappa(richiesta);
	}

	@PatchMapping("/{stageId}")
	public TappaAdminResponse modifica(@PathVariable("stageId") UUID stageId,
			@Valid @RequestBody ModificaTappaRequest richiesta) {
		return adminContenutiService.modificaTappa(stageId, richiesta);
	}

	@DeleteMapping("/{stageId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancella(@PathVariable("stageId") UUID stageId) {
		adminContenutiService.cancellaTappa(stageId);
	}

	@PostMapping("/{stageId}/lessons")
	@ResponseStatus(HttpStatus.CREATED)
	public LezioneAdminResponse creaLezione(@PathVariable("stageId") UUID stageId,
			@Valid @RequestBody CreazioneLezioneRequest richiesta) {
		return adminContenutiService.creaLezione(stageId, richiesta);
	}

}
