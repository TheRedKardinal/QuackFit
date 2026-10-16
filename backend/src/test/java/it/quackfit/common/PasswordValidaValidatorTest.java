package it.quackfit.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regole di @PasswordValida (vedi backend/CLAUDE.md, Password). */
class PasswordValidaValidatorTest {

	private final PasswordValidaValidator validatore = new PasswordValidaValidator();

	@ParameterizedTest
	@ValueSource(strings = {
			"Password1!", // caso tipico
			"Àbcdefg1!", // maiuscola accentata
			"Password 1!" // gli spazi sono ammessi (ma non contano come carattere speciale)
	})
	void passwordValide(String password) {
		assertThat(validatore.isValid(password, null)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Pass1!", // meno di 8 caratteri
			"password1!", // senza maiuscola
			"Password!!", // senza numero
			"Password12", // senza carattere speciale
			"Password 12" // lo spazio non è un carattere speciale
	})
	void passwordNonValide(String password) {
		assertThat(validatore.isValid(password, null)).isFalse();
	}

	@Test
	void settantaDueByteAmmessiSettantatreNo() {
		String da72 = "Aa1!" + "a".repeat(68);
		String da73 = "Aa1!" + "a".repeat(69);

		assertThat(da72.getBytes(StandardCharsets.UTF_8)).hasSize(72);
		assertThat(validatore.isValid(da72, null)).isTrue();
		assertThat(validatore.isValid(da73, null)).isFalse();
	}

	@Test
	void iByteSiContanoInUtf8NonICaratteri() {
		// 40 caratteri, ma "è" vale 2 byte: 4 + 36 × 2 = 76 byte
		String accentata = "Aa1!" + "è".repeat(36);

		assertThat(accentata).hasSize(40);
		assertThat(validatore.isValid(accentata, null)).isFalse();
	}

	@Test
	void nullVuotaOSpaziLiControllaNotBlank() {
		// così una password vuota ha un solo messaggio: "La password è obbligatoria"
		assertThat(validatore.isValid(null, null)).isTrue();
		assertThat(validatore.isValid("", null)).isTrue();
		assertThat(validatore.isValid("   ", null)).isTrue();
	}

}
