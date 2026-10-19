package it.quackfit.contenuti;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.quackfit.utente.Utente;

/** Percorso dell'utente loggato (percorso.md): utente autenticato con email verificata (regola in SecurityConfig). */
@RestController
@RequestMapping("/api/path")
public class PercorsoController {

	private final PercorsoService percorsoService;

	public PercorsoController(PercorsoService percorsoService) {
		this.percorsoService = percorsoService;
	}

	@GetMapping("/stages")
	public List<TappaPercorsoResponse> mappa(@AuthenticationPrincipal Utente utente) {
		return percorsoService.mappa(utente.getId());
	}

}
