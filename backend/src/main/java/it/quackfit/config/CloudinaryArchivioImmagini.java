package it.quackfit.config;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Caricamento ed eliminazione su Cloudinary con le API REST upload e destroy, chiamate con l'HttpClient del JDK
 * (come Brevo): nessuna dipendenza in più.
 *
 * - Richiesta firmata: i parametri (tranne file e api_key) si firmano con l'api_secret, che non viaggia mai (vedi firma).
 * - Il file viaggia come data URI base64 in un form urlencoded, così non serve costruire a mano un corpo multipart.
 * - overwrite=true e invalidate=true: lo stesso id pubblico sostituisce la foto precedente e svuota la cache della CDN.
 *   L'URL restituito contiene la versione (v123...), quindi cambia a ogni foto e il browser non mostra quella vecchia.
 *
 * Chiavi (vedi backend/CLAUDE.md, Configurazione): facoltative in locale e nei test (senza chiavi il caricamento
 * fallisce con un errore chiaro), obbligatorie all'avvio nel profilo prod (quackfit.cloudinary.chiavi-obbligatorie).
 */
@Slf4j
@Service
public class CloudinaryArchivioImmagini implements ArchivioImmagini {

	static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final HttpClient httpClient;
	private final String urlBase;
	private final String cloudName;
	private final String apiKey;
	private final String apiSecret;

	public CloudinaryArchivioImmagini(ObjectMapper objectMapper, Clock clock,
			@Value("${quackfit.cloudinary.url}") String urlBase,
			@Value("${quackfit.cloudinary.cloud-name}") String cloudName,
			@Value("${quackfit.cloudinary.api-key}") String apiKey,
			@Value("${quackfit.cloudinary.api-secret}") String apiSecret,
			@Value("${quackfit.cloudinary.chiavi-obbligatorie}") boolean chiaviObbligatorie) {
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
		this.urlBase = urlBase;
		this.cloudName = cloudName;
		this.apiKey = apiKey;
		this.apiSecret = apiSecret;
		if (chiaviObbligatorie && !chiaviMancanti().isEmpty()) {
			throw new IllegalStateException("Chiavi Cloudinary mancanti: " + chiaviMancanti());
		}
	}

	/** I nomi delle variabili d'ambiente vuote (mai i valori). */
	private List<String> chiaviMancanti() {
		List<String> mancanti = new ArrayList<>();
		if (cloudName.isBlank()) {
			mancanti.add("CLOUDINARY_CLOUD_NAME");
		}
		if (apiKey.isBlank()) {
			mancanti.add("CLOUDINARY_API_KEY");
		}
		if (apiSecret.isBlank()) {
			mancanti.add("CLOUDINARY_API_SECRET");
		}
		return mancanti;
	}

	@Override
	public String carica(byte[] contenuto, String tipoMime, String idPubblico) {
		Map<String, String> file = Map.of(
				"file", "data:" + tipoMime + ";base64," + Base64.getEncoder().encodeToString(contenuto));
		return urlSicuro(chiama("upload", parametriFirmati(idPubblico, true), file));
	}

	/**
	 * API destroy: elimina l'immagine e svuota la cache della CDN (invalidate). Cloudinary risponde
	 * {"result": "ok"} oppure {"result": "not found"} se non c'era: in entrambi i casi l'immagine non esiste più.
	 */
	@Override
	public void elimina(String idPubblico) {
		String risultato = leggi(chiama("destroy", parametriFirmati(idPubblico, false), Map.of()), "result");
		if (!risultato.equals("ok") && !risultato.equals("not found")) {
			throw new CaricamentoImmagineFallitoException("eliminazione su Cloudinary non riuscita");
		}
		log.info("Eliminazione su Cloudinary: {}", risultato);
	}

	/** Parametri da firmare: invalidate, public_id, timestamp e, solo per l'upload, overwrite. */
	private SortedMap<String, String> parametriFirmati(String idPubblico, boolean sovrascrivi) {
		SortedMap<String, String> parametri = new TreeMap<>(Map.of(
				"invalidate", "true",
				"public_id", idPubblico,
				"timestamp", String.valueOf(Instant.now(clock).getEpochSecond())));
		if (sovrascrivi) {
			parametri.put("overwrite", "true");
		}
		return parametri;
	}

	/**
	 * POST firmato a {url}/{cloud}/image/{azione}, come form urlencoded. I campi `extra` (il file) non si firmano,
	 * come api_key. Restituisce il corpo della risposta; ogni errore diventa CaricamentoImmagineFallitoException
	 * con solo lo stato HTTP o il tipo dell'errore nel messaggio.
	 */
	private String chiama(String azione, SortedMap<String, String> daFirmare, Map<String, String> extra) {
		if (!chiaviMancanti().isEmpty()) {
			throw new CaricamentoImmagineFallitoException("chiavi Cloudinary mancanti: " + chiaviMancanti());
		}
		Map<String, String> corpo = new LinkedHashMap<>(daFirmare);
		corpo.put("signature", firma(daFirmare, apiSecret));
		corpo.put("api_key", apiKey);
		corpo.putAll(extra);

		HttpRequest richiesta = HttpRequest.newBuilder(URI.create(urlBase + "/" + cloudName + "/image/" + azione))
				.timeout(TIMEOUT)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(formUrlEncoded(corpo)))
				.build();

		HttpResponse<String> risposta;
		try {
			risposta = httpClient.send(richiesta, HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			// comprende il timeout (HttpTimeoutException): si registra solo il tipo
			throw new CaricamentoImmagineFallitoException("Cloudinary non raggiungibile (" + e.getClass().getSimpleName() + ")");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CaricamentoImmagineFallitoException("richiesta a Cloudinary interrotta");
		}
		if (risposta.statusCode() < 200 || risposta.statusCode() >= 300) {
			throw new CaricamentoImmagineFallitoException("Cloudinary ha risposto con lo stato HTTP " + risposta.statusCode());
		}
		return risposta.body();
	}

	/** Un campo di testo della risposta JSON ("" se manca); risposta non leggibile → fallita. */
	private String leggi(String corpoRisposta, String campo) {
		try {
			JsonNode json = objectMapper.readTree(corpoRisposta);
			return json.path(campo).asString("");
		} catch (RuntimeException e) {
			throw new CaricamentoImmagineFallitoException("risposta di Cloudinary non leggibile");
		}
	}

	/** L'URL https della risposta (secure_url): se manca o non è https, il caricamento conta come fallito. */
	private String urlSicuro(String corpoRisposta) {
		String url = leggi(corpoRisposta, "secure_url");
		if (!url.startsWith("https://")) {
			throw new CaricamentoImmagineFallitoException("risposta di Cloudinary senza secure_url https");
		}
		return url;
	}

	/**
	 * Firma di Cloudinary: i parametri da firmare in ordine alfabetico, uniti come chiave=valore con "&",
	 * seguiti dall'api_secret; il tutto passato in SHA-1 ed espresso in esadecimale.
	 * Cloudinary rifà lo stesso calcolo con il suo secret: se le firme coincidono, la richiesta viene da chi
	 * conosce il secret e i parametri non sono stati cambiati. Il secret non viaggia mai nella richiesta.
	 */
	static String firma(SortedMap<String, String> parametri, String apiSecret) {
		String testo = parametri.entrySet().stream()
				.map(voce -> voce.getKey() + "=" + voce.getValue())
				.collect(Collectors.joining("&")) + apiSecret;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-1").digest(testo.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			// SHA-1 c'è in ogni JVM: non può succedere
			throw new IllegalStateException(e);
		}
	}

	private static String formUrlEncoded(Map<String, String> campi) {
		return campi.entrySet().stream()
				.map(voce -> URLEncoder.encode(voce.getKey(), StandardCharsets.UTF_8) + "="
						+ URLEncoder.encode(voce.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
	}

}
