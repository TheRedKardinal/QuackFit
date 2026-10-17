package it.quackfit.utente;

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;
import it.quackfit.common.PasswordValidaValidator;

/**
 * Controllo della password attuale per le operazioni sul proprio account (cambio password, cambio email,
 * cancellazione: vedi me.md, Regole comuni). Un unico punto, così la regola dei 72 byte vale ovunque.
 */
@Component
public class PasswordAttuale {

	static final String MSG_PASSWORD_ATTUALE_ERRATA = "La password attuale non è corretta";

	private final PasswordEncoder passwordEncoder;

	public PasswordAttuale(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Password sbagliata → 400 PASSWORD_ATTUALE_ERRATA (400 e non 401: chi la sbaglia non viene disconnesso).
	 * Oltre i 72 byte conta come sbagliata senza chiamare BCrypt, che lancerebbe un'eccezione.
	 */
	public void controlla(String password, Utente utente) {
		boolean troppoLunga = password.getBytes(StandardCharsets.UTF_8).length > PasswordValidaValidator.MASSIMO_BYTE;
		if (troppoLunga || !passwordEncoder.matches(password, utente.getPassword())) {
			throw new ApplicazioneException(CodiceErrore.PASSWORD_ATTUALE_ERRATA, MSG_PASSWORD_ATTUALE_ERRATA);
		}
	}

}
