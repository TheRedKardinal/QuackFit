package it.quackfit.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** Rendering dei template delle email, con un motore Thymeleaf configurato come quello di Spring Boot. */
class ComposizioneEmailTest {

	private ComposizioneEmail composizione;

	@BeforeEach
	void setUp() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
		SpringTemplateEngine engine = new SpringTemplateEngine();
		engine.setTemplateResolver(resolver);
		composizione = new ComposizioneEmail(engine);
	}

	@Test
	void verificaEmail() {
		Email email = composizione.verificaEmail("mario@example.com", "Mario", "123456");

		assertThat(email.destinatario()).isEqualTo("mario@example.com");
		assertThat(email.oggetto()).isEqualTo("Il tuo codice di verifica QuackFit");
		assertThat(email.html())
				.contains("Ciao <span>Mario</span>,")
				.contains("123456")
				.contains("verificare il tuo indirizzo email")
				.contains("Il codice vale 15 minuti")
				.contains("<title>Il tuo codice di verifica QuackFit</title>");
	}

	@Test
	void resetPassword() {
		Email email = composizione.resetPassword("mario@example.com", "Mario", "654321");

		assertThat(email.oggetto()).isEqualTo("Reimposta la password di QuackFit");
		assertThat(email.html()).contains("654321").contains("reimpostare la tua password").contains("Mario");
	}

	@Test
	void cambioEmail() {
		Email email = composizione.cambioEmail("nuova@example.com", "Mario", "111222");

		assertThat(email.destinatario()).isEqualTo("nuova@example.com");
		assertThat(email.oggetto()).isEqualTo("Conferma il nuovo indirizzo email");
		assertThat(email.html()).contains("111222").contains("confermare questo nuovo indirizzo").contains("Mario");
	}

	@Test
	void ilNomeVieneEscapatoNonDiventaHtml() {
		Email email = composizione.verificaEmail("mario@example.com", "<script>alert(1)</script>", "123456");

		assertThat(email.html())
				.doesNotContain("<script>")
				.contains("&lt;script&gt;alert(1)&lt;/script&gt;");
	}

	@Test
	void iSegnapostoDeiTemplateVengonoSostituiti() {
		Email email = composizione.verificaEmail("mario@example.com", "Mario", "123456");

		assertThat(email.html()).doesNotContain("000000").doesNotContain(">Nome<")
				.doesNotContain("th:text").doesNotContain("xmlns:th");
	}

}
