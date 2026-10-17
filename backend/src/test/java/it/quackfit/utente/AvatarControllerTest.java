package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import it.quackfit.config.ArchivioImmagini;
import it.quackfit.config.CaricamentoImmagineFallitoException;
import it.quackfit.security.JwtService;

/**
 * Test di integrazione di PATCH /api/me/avatar con H2 e l'archivio delle immagini simulato:
 * nessuna chiamata a Cloudinary vero.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvatarControllerTest {

	private static final String URL_1 = "https://res.cloudinary.com/q/image/upload/v1/avatar/m.png";
	private static final String URL_2 = "https://res.cloudinary.com/q/image/upload/v2/avatar/m.jpg";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private StreakRepository streakRepository;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private ArchivioImmagini archivio;

	private Utente mario;
	private String token;

	@BeforeEach
	void setUp() {
		pulisci();
		mario = new Utente("Mario", "Rossi", "mario@example.com", "hash", Instant.parse("2026-01-01T10:00:00Z"));
		mario.setEmailVerificata(true);
		mario = utenteRepository.save(mario);
		streakRepository.save(new Streak(mario, Instant.parse("2026-01-01T10:00:00Z")));
		token = "Bearer " + jwtService.genera(mario);
	}

	@AfterEach
	void pulisci() {
		streakRepository.deleteAll();
		utenteRepository.deleteAll();
	}

	private ResultActions carica(String nome, String tipo, byte[] contenuto) throws Exception {
		return mockMvc.perform(multipart(HttpMethod.PATCH, "/api/me/avatar")
				.file(new MockMultipartFile("file", nome, tipo, contenuto))
				.header(HttpHeaders.AUTHORIZATION, token));
	}

	private String fotoSalvata() {
		return utenteRepository.findById(mario.getId()).orElseThrow().getFotoProfilo();
	}

	private static void fileNonValido(ResultActions risposta) throws Exception {
		risposta.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("FILE_NON_VALIDO"));
	}

	@Test
	void fotoValidaSalvataERestituita() throws Exception {
		when(archivio.carica(AvatarServiceTest.PNG, "image/png", "avatar/" + mario.getId())).thenReturn(URL_1);

		carica("foto.png", "image/png", AvatarServiceTest.PNG)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fotoProfilo").value(URL_1));

		assertThat(fotoSalvata()).isEqualTo(URL_1);
	}

	@Test
	void unaNuovaFotoSostituisceLaVecchiaConLoStessoId() throws Exception {
		when(archivio.carica(any(), eq("image/png"), anyString())).thenReturn(URL_1);
		when(archivio.carica(any(), eq("image/jpeg"), anyString())).thenReturn(URL_2);

		carica("prima.png", "image/png", AvatarServiceTest.PNG).andExpect(status().isOk());
		carica("seconda.jpg", "image/jpeg", AvatarServiceTest.JPEG).andExpect(status().isOk());

		assertThat(fotoSalvata()).isEqualTo(URL_2);
		verify(archivio).carica(AvatarServiceTest.PNG, "image/png", "avatar/" + mario.getId());
		verify(archivio).carica(AvatarServiceTest.JPEG, "image/jpeg", "avatar/" + mario.getId());
	}

	@Test
	void webpAmmesso() throws Exception {
		when(archivio.carica(any(), eq("image/webp"), anyString())).thenReturn(URL_1);

		carica("foto.webp", "image/webp", AvatarServiceTest.WEBP).andExpect(status().isOk());
	}

	@Test
	void formatoNonAmmesso() throws Exception {
		fileNonValido(carica("foto.gif", "image/gif", "GIF89a....".getBytes(StandardCharsets.US_ASCII)));
		verify(archivio, never()).carica(any(), any(), any());
	}

	@Test
	void fileConEstensioneFalsa() throws Exception {
		fileNonValido(carica("foto.png", "image/png", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));
		assertThat(fotoSalvata()).isNull();
	}

	@Test
	void fileOltre2Mega() throws Exception {
		byte[] troppo = new byte[(int) AvatarService.MASSIMO_BYTE + 1];
		System.arraycopy(AvatarServiceTest.PNG, 0, troppo, 0, AvatarServiceTest.PNG.length);

		fileNonValido(carica("grande.png", "image/png", troppo));
	}

	@Test
	void fileVuotoOMancante() throws Exception {
		fileNonValido(carica("vuoto.png", "image/png", new byte[0]));
		fileNonValido(mockMvc.perform(multipart(HttpMethod.PATCH, "/api/me/avatar")
				.header(HttpHeaders.AUTHORIZATION, token)));
	}

	@Test
	void richiestaNonMultipart() throws Exception {
		fileNonValido(mockMvc.perform(patch("/api/me/avatar")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"file\": \"foto.png\"}")));
	}

	@Test
	void erroreDiCloudinary500EFotoPrecedenteInvariata() throws Exception {
		when(archivio.carica(any(), anyString(), anyString())).thenReturn(URL_1);
		carica("prima.png", "image/png", AvatarServiceTest.PNG).andExpect(status().isOk());
		when(archivio.carica(any(), anyString(), anyString()))
				.thenThrow(new CaricamentoImmagineFallitoException("Cloudinary non raggiungibile (HttpTimeoutException)"));

		carica("seconda.png", "image/png", AvatarServiceTest.PNG)
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.codice").value("ERRORE_INTERNO"))
				.andExpect(jsonPath("$.messaggio").value("Si è verificato un errore imprevisto, riprova più tardi"));

		assertThat(fotoSalvata()).isEqualTo(URL_1);
	}

	@Test
	void conEmailNonVerificata403() throws Exception {
		mario.setEmailVerificata(false);
		utenteRepository.save(mario);

		carica("foto.png", "image/png", AvatarServiceTest.PNG)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codice").value("EMAIL_NON_VERIFICATA"));
		verify(archivio, never()).carica(any(), any(), any());
	}

}
