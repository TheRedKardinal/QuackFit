package it.quackfit.common;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Controlla le regole di {@link PasswordValida}. */
public class PasswordValidaValidator implements ConstraintValidator<PasswordValida, String> {

	static final int MINIMO_CARATTERI = 8;
	static final int MASSIMO_BYTE = 72;

	@Override
	public boolean isValid(String password, ConstraintValidatorContext contesto) {
		// vuota o di soli spazi: la segnala @NotBlank, così il campo ha un solo messaggio
		if (password == null || password.isBlank()) {
			return true;
		}
		// i caratteri si contano come code point (un'emoji è un carattere solo)
		return password.codePointCount(0, password.length()) >= MINIMO_CARATTERI
				&& password.getBytes(StandardCharsets.UTF_8).length <= MASSIMO_BYTE
				&& password.codePoints().anyMatch(Character::isUpperCase)
				&& password.codePoints().anyMatch(c -> c >= '0' && c <= '9')
				&& password.codePoints().anyMatch(PasswordValidaValidator::speciale);
	}

	/** Né lettera, né cifra, né spazio. */
	private static boolean speciale(int c) {
		return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
	}

}
