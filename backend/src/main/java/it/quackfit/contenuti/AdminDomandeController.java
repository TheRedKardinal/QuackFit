package it.quackfit.contenuti;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Modifica e cancellazione di una domanda (vedi admin.md, Domande). La creazione sta in AdminLezioniController,
 * perché il percorso parte dalla lezione. Solo ADMIN e SUPERADMIN con email verificata.
 */
@RestController
@RequestMapping("/api/admin/questions")
public class AdminDomandeController {

	private final AdminDomandeService adminDomandeService;

	public AdminDomandeController(AdminDomandeService adminDomandeService) {
		this.adminDomandeService = adminDomandeService;
	}

	@PatchMapping("/{questionId}")
	public DomandaAdminResponse modifica(@PathVariable("questionId") UUID questionId,
			@Valid @RequestBody ModificaDomandaRequest richiesta) {
		return adminDomandeService.modifica(questionId, richiesta);
	}

	@DeleteMapping("/{questionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancella(@PathVariable("questionId") UUID questionId) {
		adminDomandeService.cancella(questionId);
	}

}
