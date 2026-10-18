package it.quackfit.contenuti;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Dettaglio, modifica e cancellazione di una lezione (vedi admin.md, Lezioni). La creazione sta in
 * AdminTappeController, perché il percorso parte dalla tappa. Solo ADMIN e SUPERADMIN con email verificata.
 */
@RestController
@RequestMapping("/api/admin/lessons")
public class AdminLezioniController {

	private final AdminContenutiService adminContenutiService;

	public AdminLezioniController(AdminContenutiService adminContenutiService) {
		this.adminContenutiService = adminContenutiService;
	}

	@GetMapping("/{lessonId}")
	public LezioneDettaglioAdminResponse dettaglio(@PathVariable("lessonId") UUID lessonId) {
		return adminContenutiService.dettaglioLezione(lessonId);
	}

	@PatchMapping("/{lessonId}")
	public LezioneAdminResponse modifica(@PathVariable("lessonId") UUID lessonId,
			@Valid @RequestBody ModificaLezioneRequest richiesta) {
		return adminContenutiService.modificaLezione(lessonId, richiesta);
	}

	@DeleteMapping("/{lessonId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancella(@PathVariable("lessonId") UUID lessonId) {
		adminContenutiService.cancellaLezione(lessonId);
	}

}
