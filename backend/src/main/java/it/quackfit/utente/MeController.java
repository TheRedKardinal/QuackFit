package it.quackfit.utente;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
	private final AvatarService avatarService;
	private final CambioEmailService cambioEmailService;
	private final VerificaEmailService verificaEmailService;

	public MeController(MeService meService, AvatarService avatarService, CambioEmailService cambioEmailService,
			VerificaEmailService verificaEmailService) {
		this.meService = meService;
		this.avatarService = avatarService;
		this.cambioEmailService = cambioEmailService;
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

	/**
	 * Multipart con il campo "file". Il file è facoltativo per Spring (required = false): se manca, o la richiesta
	 * non è multipart, risponde il service con 400 FILE_NON_VALIDO invece di un errore generico.
	 */
	@PatchMapping("/avatar")
	public AvatarResponse aggiornaAvatar(@AuthenticationPrincipal Utente utente,
			@RequestParam(name = "file", required = false) MultipartFile file) {
		return avatarService.aggiorna(utente.getId(), file);
	}

	/** Invia il codice alla nuova email; l'email dell'utente cambia solo con la conferma. */
	@PostMapping("/email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void richiediCambioEmail(@AuthenticationPrincipal Utente utente, @Valid @RequestBody CambioEmailRequest richiesta) {
		cambioEmailService.richiedi(utente.getId(), richiesta);
	}

	/** Logout ovunque: dopo il 204 anche il token usato per la chiamata non vale più. */
	@PostMapping("/email/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void confermaCambioEmail(@AuthenticationPrincipal Utente utente,
			@Valid @RequestBody ConfermaCambioEmailRequest richiesta) {
		cambioEmailService.conferma(utente.getId(), richiesta.codice());
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
