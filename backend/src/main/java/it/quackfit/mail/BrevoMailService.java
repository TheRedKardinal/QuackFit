package it.quackfit.mail;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

/**
 * Invio tramite l'API HTTP di Brevo (profilo prod), con l'HttpClient del JDK: nessuna dipendenza in più.
 * Nel log finisce solo lo stato HTTP: mai il corpo della richiesta (contiene il codice) né la chiave API.
 */
@Service
@ConditionalOnProperty(name = "quackfit.mail.modalita", havingValue = "brevo")
public class BrevoMailService extends MailServiceBase {

	static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;
	private final URI url;
	private final String apiKey;
	private final String mittente;
	private final String nomeMittente;

	public BrevoMailService(ComposizioneEmail composizione, ObjectMapper objectMapper,
			@Value("${quackfit.mail.brevo.url}") String url,
			@Value("${quackfit.mail.brevo.api-key}") String apiKey,
			@Value("${quackfit.mail.mittente}") String mittente,
			@Value("${quackfit.mail.nome-mittente}") String nomeMittente) {
		super(composizione);
		if (apiKey.isBlank() || mittente.isBlank()) {
			throw new IllegalStateException("BREVO_API_KEY e MAIL_MITTENTE servono con MAIL_MODALITA=brevo");
		}
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
		this.url = URI.create(url);
		this.apiKey = apiKey;
		this.mittente = mittente;
		this.nomeMittente = nomeMittente;
	}

	@Override
	protected void consegna(Email email) {
		Map<String, Object> corpo = Map.of(
				"sender", Map.of("name", nomeMittente, "email", mittente),
				"to", List.of(Map.of("email", email.destinatario())),
				"subject", email.oggetto(),
				"htmlContent", email.html());
		HttpRequest richiesta = HttpRequest.newBuilder(url)
				.timeout(TIMEOUT)
				.header("api-key", apiKey)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(corpo)))
				.build();
		int stato;
		try {
			stato = httpClient.send(richiesta, HttpResponse.BodyHandlers.discarding()).statusCode();
		} catch (IOException e) {
			// comprende il timeout (HttpTimeoutException): si registra solo il tipo
			throw new InvioEmailFallitoException("Brevo non raggiungibile (" + e.getClass().getSimpleName() + ")");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InvioEmailFallitoException("invio a Brevo interrotto");
		}
		if (stato < 200 || stato >= 300) {
			throw new InvioEmailFallitoException("Brevo ha risposto con lo stato HTTP " + stato);
		}
	}

}
