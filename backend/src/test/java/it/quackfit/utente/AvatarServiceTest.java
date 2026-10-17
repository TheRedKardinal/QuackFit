package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;
import it.quackfit.config.ArchivioImmagini;
import it.quackfit.config.CaricamentoImmagineFallitoException;

/** Unit test di AvatarService con l'archivio delle immagini simulato. */
class AvatarServiceTest {

	static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10 };
	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D };
	static final byte[] WEBP = "RIFF$\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
	private static final String URL = "https://res.cloudinary.com/q/image/upload/v2/avatar/x.png";

	private ArchivioImmagini archivio;
	private UtenteRepository utenteRepository;
	private AvatarService servizio;
	private final UUID utenteId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		archivio = mock(ArchivioImmagini.class);
		utenteRepository = mock(UtenteRepository.class);
		servizio = new AvatarService(archivio, utenteRepository);
	}

	private static MockMultipartFile file(String nome, String tipoDichiarato, byte[] contenuto) {
		return new MockMultipartFile("file", nome, tipoDichiarato, contenuto);
	}

	private static void fileNonValido(Runnable azione) {
		assertThatThrownBy(azione::run)
				.isInstanceOfSatisfying(ApplicazioneException.class,
						e -> assertThat(e.getCodice()).isEqualTo(CodiceErrore.FILE_NON_VALIDO));
	}

	// --- tipo riconosciuto dal contenuto ---

	@Test
	void riconosceJpegPngWebpDaiPrimiByte() {
		assertThat(AvatarService.tipoImmagine(JPEG)).contains("image/jpeg");
		assertThat(AvatarService.tipoImmagine(PNG)).contains("image/png");
		assertThat(AvatarService.tipoImmagine(WEBP)).contains("image/webp");
	}

	@Test
	void nonRiconosceAltriFormatiNeFileTroppoCorti() {
		assertThat(AvatarService.tipoImmagine("GIF89a....".getBytes(StandardCharsets.US_ASCII))).isEmpty();
		assertThat(AvatarService.tipoImmagine("RIFF\0\0\0\0WAVE".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
		assertThat(AvatarService.tipoImmagine(new byte[] { (byte) 0xFF, (byte) 0xD8 })).isEmpty();
		assertThat(AvatarService.tipoImmagine(new byte[0])).isEmpty();
	}

	// --- caricamento ---

	@Test
	void caricaConIdFissoESalvaLUrl() {
		when(archivio.carica(PNG, "image/png", "avatar/" + utenteId)).thenReturn(URL);

		AvatarResponse risposta = servizio.aggiorna(utenteId, file("foto.png", "image/png", PNG));

		assertThat(risposta.fotoProfilo()).isEqualTo(URL);
		verify(utenteRepository).aggiornaFotoProfilo(utenteId, URL);
	}

	@Test
	void ilTipoDichiaratoEdLEstensioneNonContano() {
		when(archivio.carica(any(), anyString(), anyString())).thenReturn(URL);

		// un vero JPEG chiamato .txt e dichiarato come testo: vale il contenuto
		servizio.aggiorna(utenteId, file("foto.txt", "text/plain", JPEG));

		verify(archivio).carica(JPEG, "image/jpeg", "avatar/" + utenteId);
	}

	@Test
	void fileFintoConEstensioneDiImmagineRifiutato() {
		fileNonValido(() -> servizio.aggiorna(utenteId,
				file("foto.png", "image/png", "non sono un'immagine".getBytes(StandardCharsets.UTF_8))));
		verifyNoInteractions(archivio);
	}

	@Test
	void fileMancanteOVuoto() {
		fileNonValido(() -> servizio.aggiorna(utenteId, null));
		fileNonValido(() -> servizio.aggiorna(utenteId, file("vuoto.png", "image/png", new byte[0])));
		verifyNoInteractions(archivio);
	}

	@Test
	void dueMegaAmmessiUnByteInPiuNo() {
		when(archivio.carica(any(), anyString(), anyString())).thenReturn(URL);
		byte[] dueMega = new byte[(int) AvatarService.MASSIMO_BYTE];
		System.arraycopy(PNG, 0, dueMega, 0, PNG.length);
		byte[] troppo = new byte[(int) AvatarService.MASSIMO_BYTE + 1];
		System.arraycopy(PNG, 0, troppo, 0, PNG.length);

		servizio.aggiorna(utenteId, file("grande.png", "image/png", dueMega));
		fileNonValido(() -> servizio.aggiorna(utenteId, file("troppo.png", "image/png", troppo)));
	}

	@Test
	void erroreDiCloudinaryDiventaErroreInternoEFotoInvariata() {
		when(archivio.carica(any(), anyString(), anyString()))
				.thenThrow(new CaricamentoImmagineFallitoException("Cloudinary ha risposto con lo stato HTTP 500"));

		assertThatThrownBy(() -> servizio.aggiorna(utenteId, file("foto.png", "image/png", PNG)))
				.isInstanceOfSatisfying(ApplicazioneException.class, e -> {
					assertThat(e.getCodice()).isEqualTo(CodiceErrore.ERRORE_INTERNO);
					assertThat(e.getMessage()).doesNotContain("Cloudinary");
				});
		verify(utenteRepository, never()).aggiornaFotoProfilo(any(), any());
	}

}
