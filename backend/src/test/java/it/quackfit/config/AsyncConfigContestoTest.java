package it.quackfit.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * AsyncConfig dentro un contesto Spring vero: Spring lo avvia come SmartLifecycle e lo ferma alla chiusura
 * del contesto (è lì che, fuori dai test, si aspettano i lavori in coda).
 */
class AsyncConfigContestoTest {

	@Configuration
	@Import(AsyncConfig.class)
	static class SoloAsync {
	}

	@Test
	void springLoAvviaELoFermaAllaChiusura() {
		SpringApplication applicazione = new SpringApplication(SoloAsync.class);
		applicazione.setWebApplicationType(WebApplicationType.NONE);
		applicazione.setAdditionalProfiles("test");
		AsyncConfig config;
		try (ConfigurableApplicationContext contesto = applicazione.run()) {
			config = contesto.getBean(AsyncConfig.class);
			assertThat(config.isRunning()).isTrue();
		}
		assertThat(config.isRunning()).isFalse();
	}

}
