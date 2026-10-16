package it.quackfit.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Implementazione Brevo contro un server HTTP finto (HttpServer incluso nel JDK, nessuna libreria in più).
 */
@ExtendWith(OutputCaptureExtension.class)
class BrevoMailServiceTest {

	private static final String DESTINATARIO = "mario.rossi@example.com";
	private static final String CODICE = "135790";
	private static final String API_KEY = "chiave-finta-dei-test";

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	private HttpServer server;
	private int statoDaRispondere;
	private final AtomicReference<String> apiKeyRicevuta = new AtomicReference<>();
	private final AtomicReference<String> corpoRicevuto = new AtomicReference<>();

	@BeforeEach
	void avviaServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v3/smtp/email", scambio -> {
			apiKeyRicevuta.set(scambio.getRequestHeaders().getFirst("api-key"));
			corpoRicevuto.set(new String(scambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			scambio.sendResponseHeaders(statoDaRispondere, -1);
			scambio.close();
		});
		server.start();
	}

	@AfterEach
	void fermaServer() {
		server.stop(0);
	}

	private String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email";
	}

	private BrevoMailService servizio(String url) {
		ComposizioneEmail composizione = mock(ComposizioneEmail.class);
		when(composizione.verificaEmail(DESTINATARIO, "Mario", CODICE)).thenReturn(
				new Email(DESTINATARIO, "Il tuo codice di verifica QuackFit", "<p>" + CODICE + "</p>", CODICE));
		return new BrevoMailService(composizione, objectMapper, url, API_KEY, "noreply@quackfit.it", "QuackFit");
	}

	@Test
	void inviaLaRichiestaNelFormatoDiBrevo(CapturedOutput output) {
		statoDaRispondere = 201;

		servizio(url()).inviaVerificaEmail(DESTINATARIO, "Mario", CODICE);

		assertThat(apiKeyRicevuta.get()).isEqualTo(API_KEY);
		JsonNode corpo = objectMapper.readTree(corpoRicevuto.get());
		assertThat(corpo.get("sender").get("email").asString()).isEqualTo("noreply@quackfit.it");
		assertThat(corpo.get("sender").get("name").asString()).isEqualTo("QuackFit");
		assertThat(corpo.get("to").get(0).get("email").asString()).isEqualTo(DESTINATARIO);
		assertThat(corpo.get("subject").asString()).isEqualTo("Il tuo codice di verifica QuackFit");
		assertThat(corpo.get("htmlContent").asString()).contains(CODICE);
		assertThat(output).doesNotContain("non riuscito");
	}

	@Test
	void rispostaDiErroreNonLanciaEccezioniENelLogSoloLoStato(CapturedOutput output) {
		statoDaRispondere = 500;

		assertThatCode(() -> servizio(url()).inviaVerificaEmail(DESTINATARIO, "Mario", CODICE))
				.doesNotThrowAnyException();

		assertThat(output)
				.contains("m***@example.com non riuscito: Brevo ha risposto con lo stato HTTP 500")
				.doesNotContain(CODICE)
				.doesNotContain(DESTINATARIO)
				.doesNotContain(API_KEY);
	}

	@Test
	void brevoIrraggiungibileNonLanciaEccezioni(CapturedOutput output) {
		String urlSpento = url();
		server.stop(0);

		assertThatCode(() -> servizio(urlSpento).inviaVerificaEmail(DESTINATARIO, "Mario", CODICE))
				.doesNotThrowAnyException();

		assertThat(output)
				.contains("non riuscito: Brevo non raggiungibile")
				.doesNotContain(CODICE)
				.doesNotContain(API_KEY);
	}

	@Test
	void senzaChiaveOMittenteNonParte() {
		ComposizioneEmail composizione = mock(ComposizioneEmail.class);

		assertThatThrownBy(() -> new BrevoMailService(composizione, objectMapper, url(), "", "noreply@quackfit.it", "QuackFit"))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new BrevoMailService(composizione, objectMapper, url(), API_KEY, "", "QuackFit"))
				.isInstanceOf(IllegalStateException.class);
	}

}
