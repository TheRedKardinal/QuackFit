package it.quackfit.utente;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Endpoint dell'utente loggato (vedi progettazione.md, Me). L'utente arriva sempre dal principal,
 * mai da un id nell'URL o nel body. GET, /verification e /verification/resend sono ammessi anche con email
 * non verificata (regola in SecurityConfig).
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

	private final MeService meService;
	private final VerificaEmailService verificaEmailService;

	public MeController(MeService meService, VerificaEmailService verificaEmailService) {
		this.meService = meService;
		this.verificaEmailService = verificaEmailService;
	}

	@GetMapping
	public MeResponse profilo(@AuthenticationPrincipal Utente utente) {
		return meService.profilo(utente);
	}

	@PatchMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void modificaDati(@AuthenticationPrincipal Utente utente, @Valid @RequestBody ModificaDatiRequest richiesta) {
		meService.modificaDati(utente.getId(), richiesta);
	}

	/** Logout ovunque: dopo il 204 anche il token usato per la chiamata non vale più. */
	@PatchMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cambiaPassword(@AuthenticationPrincipal Utente utente, @Valid @RequestBody CambioPasswordRequest richiesta) {
		meService.cambiaPassword(utente.getId(), richiesta);
	}

	@PostMapping("/verification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verificaEmail(@AuthenticationPrincipal Utente utente, @Valid @RequestBody VerificaEmailRequest richiesta) {
		verificaEmailService.verifica(utente.getId(), richiesta.codice());
	}

	@PostMapping("/verification/resend")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reinviaCodice(@AuthenticationPrincipal Utente utente) {
		verificaEmailService.reinvia(utente.getId());
	}

}
