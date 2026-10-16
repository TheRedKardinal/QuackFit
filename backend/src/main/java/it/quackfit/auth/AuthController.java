package it.quackfit.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Endpoint pubblici di autenticazione (vedi progettazione.md, Auth). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	static final String MSG_PASSWORD_DIMENTICATA =
			"Se l'email è registrata, riceverai un codice per reimpostare la password.";

	private final AuthService authService;
	private final RecuperoPasswordService recuperoPasswordService;

	public AuthController(AuthService authService, RecuperoPasswordService recuperoPasswordService) {
		this.authService = authService;
		this.recuperoPasswordService = recuperoPasswordService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public TokenResponse registra(@Valid @RequestBody RegistrazioneRequest richiesta) {
		return authService.registra(richiesta);
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest richiesta) {
		return authService.login(richiesta);
	}

	/**
	 * Sempre 200 con lo stesso messaggio. Il lavoro sul codice parte in background (@Async),
	 * quindi la risposta non aspetta né la ricerca dell'utente né l'invio dell'email.
	 */
	@PostMapping("/password/forgot")
	public MessaggioResponse passwordDimenticata(@Valid @RequestBody PasswordDimenticataRequest richiesta) {
		recuperoPasswordService.inviaCodice(richiesta.email());
		return new MessaggioResponse(MSG_PASSWORD_DIMENTICATA);
	}

	@PostMapping("/password/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reimpostaPassword(@Valid @RequestBody ReimpostaPasswordRequest richiesta) {
		recuperoPasswordService.reimposta(richiesta);
	}

}
