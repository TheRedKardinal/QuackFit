package it.quackfit.tentativi;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import it.quackfit.utente.Utente;
import jakarta.validation.Valid;

/** Tentativi dell'utente loggato (tentativi.md): utente autenticato con email verificata (regola in SecurityConfig). */
@RestController
@RequestMapping("/api/attempts")
public class TentativiController {

	private final TentativiService tentativiService;

	public TentativiController(TentativiService tentativiService) {
		this.tentativiService = tentativiService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AvvioTentativoResponse avvia(@AuthenticationPrincipal Utente utente,
			@Valid @RequestBody AvvioTentativoRequest richiesta) {
		return tentativiService.avvia(utente.getId(), richiesta.lezioneId());
	}

	@PostMapping("/{id}/answers")
	public EsitoRispostaResponse rispondi(@AuthenticationPrincipal Utente utente, @PathVariable("id") UUID id,
			@Valid @RequestBody RispostaRequest richiesta) {
		return tentativiService.rispondi(utente.getId(), id, richiesta);
	}

}
