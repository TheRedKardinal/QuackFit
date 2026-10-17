package it.quackfit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/**
 * Client di Cloudinary contro un server HTTP finto (HttpServer del JDK): nessuna chiamata a Cloudinary vero.
 * La firma si prova con l'esempio della documentazione di Cloudinary ("Generating authentication signatures").
 */
class CloudinaryArchivioImmaginiTest {

	private static final String CLOUD = "nuvola-di-prova";
	private static final String API_KEY = "123456789012345";
	private static final String API_SECRET = "segreto-finto-dei-test";
	private static final Instant ADESSO = Instant.parse("2026-10-17T10:00:00Z");
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3 };

	private HttpServer server;
	private int statoDaRispondere;
	private String corpoDaRispondere;
	private final AtomicReference<String> percorsoRicevuto = new AtomicReference<>();
	private final AtomicReference<String> contentTypeRicevuto = new AtomicReference<>();
	private final AtomicReference<String> corpoRicevuto = new AtomicReference<>();

	@BeforeEach
	void avviaServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", scambio -> {
			percorsoRicevuto.set(scambio.getRequestURI().getPath());
			contentTypeRicevuto.set(scambio.getRequestHeaders().getFirst("Content-Type"));
			corpoRicevuto.set(new String(scambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] risposta = corpoDaRispondere.getBytes(StandardCharsets.UTF_8);
			scambio.sendResponseHeaders(statoDaRispondere, risposta.length == 0 ? -1 : risposta.length);
			scambio.getResponseBody().write(risposta);
			scambio.close();
		});
		server.start();
		statoDaRispondere = 200;
		corpoDaRispondere = "{\"secure_url\": \"https://res.cloudinary.com/" + CLOUD + "/image/upload/v1/avatar/abc.png\"}";
	}

	@AfterEach
	void fermaServer() {
		server.stop(0);
	}

	private String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private CloudinaryArchivioImmagini archivio(String url, String cloud, String apiKey, String apiSecret,
			boolean obbligatorie) {
		return new CloudinaryArchivioImmagini(JsonMapper.builder().build(), Clock.fixed(ADESSO, ZoneOffset.UTC),
				url, cloud, apiKey, apiSecret, obbligatorie);
	}

	private CloudinaryArchivioImmagini archivio() {
		return archivio(url(), CLOUD, API_KEY, API_SECRET, true);
	}

	/** Il corpo urlencoded ricevuto dal server finto, già decodificato. */
	private Map<String, String> campiRicevuti() {
		Map<String, String> campi = new HashMap<>();
		for (String coppia : corpoRicevuto.get().split("&")) {
			String[] parti = coppia.split("=", 2);
			campi.put(URLDecoder.decode(parti[0], StandardCharsets.UTF_8), URLDecoder.decode(parti[1], StandardCharsets.UTF_8));
		}
		return campi;
	}

	// --- firma ---

	@Test
	void firmaComeNellEsempioDellaDocumentazione() {
		TreeMap<String, String> parametri = new TreeMap<>(Map.of(
				"timestamp", "1315060510",
				"public_id", "sample_image",
				"eager", "w_400,h_300,c_pad|w_260,h_200,c_crop"));

		assertThat(CloudinaryArchivioImmagini.firma(parametri, "abcd"))
				.isEqualTo("bfd09f95f331f558cbd1320e67aa8d488770583e");
	}

	// --- caricamento ---

	@Test
	void caricaConLaRichiestaFirmataERestituisceLUrlHttps() {
		String url = archivio().carica(PNG, "image/png", "avatar/abc");

		assertThat(url).isEqualTo("https://res.cloudinary.com/" + CLOUD + "/image/upload/v1/avatar/abc.png");
		assertThat(percorsoRicevuto.get()).isEqualTo("/" + CLOUD + "/image/upload");
		assertThat(contentTypeRicevuto.get()).isEqualTo("application/x-www-form-urlencoded");

		Map<String, String> campi = campiRicevuti();
		assertThat(campi).containsEntry("public_id", "avatar/abc")
				.containsEntry("overwrite", "true")
				.containsEntry("invalidate", "true")
				.containsEntry("timestamp", String.valueOf(ADESSO.getEpochSecond()))
				.containsEntry("api_key", API_KEY)
				.containsEntry("file", "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG));
		// la firma è quella dei parametri ricevuti, calcolata con il secret (che non viaggia mai)
		TreeMap<String, String> firmati = new TreeMap<>(Map.of(
				"public_id", campi.get("public_id"),
				"overwrite", campi.get("overwrite"),
				"invalidate", campi.get("invalidate"),
				"timestamp", campi.get("timestamp")));
		assertThat(campi.get("signature")).isEqualTo(CloudinaryArchivioImmagini.firma(firmati, API_SECRET));
		assertThat(corpoRicevuto.get()).doesNotContain(API_SECRET);
	}

	@Test
	void rispostaDiErroreFallisceConSoloLoStato() {
		statoDaRispondere = 401;
		corpoDaRispondere = "{\"error\": {\"message\": \"Invalid Signature\"}}";

		assertThatThrownBy(() -> archivio().carica(PNG, "image/png", "avatar/abc"))
				.isInstanceOf(CaricamentoImmagineFallitoException.class)
				.hasMessage("Cloudinary ha risposto con lo stato HTTP 401");
	}

	@Test
	void cloudinaryIrraggiungibile() {
		CloudinaryArchivioImmagini archivio = archivio();
		server.stop(0);

		assertThatThrownBy(() -> archivio.carica(PNG, "image/png", "avatar/abc"))
				.isInstanceOf(CaricamentoImmagineFallitoException.class)
				.hasMessageStartingWith("Cloudinary non raggiungibile");
	}

	@Test
	void rispostaSenzaUrlHttpsContaComeFallita() {
		corpoDaRispondere = "{\"url\": \"http://res.cloudinary.com/x.png\"}";

		assertThatThrownBy(() -> archivio().carica(PNG, "image/png", "avatar/abc"))
				.isInstanceOf(CaricamentoImmagineFallitoException.class);
	}

	// --- chiavi ---

	@Test
	void inProdSenzaChiaviNonParteENominaLeVariabili() {
		assertThatThrownBy(() -> archivio(url(), CLOUD, "", " ", true))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CLOUDINARY_API_KEY")
				.hasMessageContaining("CLOUDINARY_API_SECRET")
				.hasMessageNotContaining("CLOUDINARY_CLOUD_NAME")
				.hasMessageNotContaining(CLOUD);
	}

	@Test
	void fuoriDaProdSenzaChiaviPartePoiIlCaricamentoFallisceConUnErroreChiaro() {
		assertThatCode(() -> archivio(url(), "", "", "", false)).doesNotThrowAnyException();

		assertThatThrownBy(() -> archivio(url(), "", API_KEY, API_SECRET, false).carica(PNG, "image/png", "avatar/abc"))
				.isInstanceOf(CaricamentoImmagineFallitoException.class)
				.hasMessage("chiavi Cloudinary mancanti: [CLOUDINARY_CLOUD_NAME]")
				.hasMessageNotContaining(API_KEY)
				.hasMessageNotContaining(API_SECRET);
		assertThat(corpoRicevuto.get()).isNull();
	}

}
