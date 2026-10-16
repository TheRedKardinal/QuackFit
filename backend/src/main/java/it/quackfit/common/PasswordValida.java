package it.quackfit.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Regole della password (vedi backend/CLAUDE.md, Password): almeno 8 caratteri, una maiuscola, un numero,
 * un carattere speciale e al massimo 72 byte in UTF-8 (limite di BCrypt).
 * Un valore null, vuoto o di soli spazi è considerato valido: l'obbligatorietà la controlla @NotBlank.
 */
@Documented
@Constraint(validatedBy = PasswordValidaValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordValida {

	String message() default "La password deve avere almeno 8 caratteri, una maiuscola, un numero e un carattere speciale"
			+ " (massimo 72 byte)";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
