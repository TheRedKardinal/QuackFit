package it.quackfit.utente;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;
import it.quackfit.config.ArchivioImmagini;
import it.quackfit.config.CaricamentoImmagineFallitoException;
import lombok.extern.slf4j.Slf4j;

/**
 * Foto profilo (vedi me.md, Foto profilo): jpg, png o webp fino a 2 MB, con il tipo riconosciuto dal contenuto.
 * Prima si carica su Cloudinary, fuori da ogni transazione (una chiamata HTTP lenta non tiene occupata una
 * connessione al database); poi una UPDATE della sola colonna salva l'URL. Se Cloudinary fallisce, l'URL salvato
 * resta quello di prima.
 */
@Slf4j
@Service
public class AvatarService {

	static final long MASSIMO_BYTE = 2L * 1024 * 1024;
	static final String MSG_FILE_NON_VALIDO = "Immagine non valida: usa un file jpg, png o webp fino a 2 MB";
	static final String MSG_ERRORE_INTERNO = "Si è verificato un errore imprevisto, riprova più tardi";

	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
	private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

	private final ArchivioImmagini archivio;
	private final UtenteRepository utenteRepository;

	public AvatarService(ArchivioImmagini archivio, UtenteRepository utenteRepository) {
		this.archivio = archivio;
		this.utenteRepository = utenteRepository;
	}

	/** Id pubblico fisso per utente: una nuova foto sostituisce la vecchia, non c'è nulla da cancellare. */
	static String idPubblico(UUID utenteId) {
		return "avatar/" + utenteId;
	}

	public AvatarResponse aggiorna(UUID utenteId, MultipartFile file) {
		if (file == null || file.isEmpty() || file.getSize() > MASSIMO_BYTE) {
			throw fileNonValido();
		}
		byte[] contenuto;
		try {
			contenuto = file.getBytes();
		} catch (IOException e) {
			throw fileNonValido();
		}
		String tipo = tipoImmagine(contenuto).orElseThrow(AvatarService::fileNonValido);

		String url;
		try {
			url = archivio.carica(contenuto, tipo, idPubblico(utenteId));
		} catch (CaricamentoImmagineFallitoException e) {
			// nel log il motivo (stato HTTP, tipo d'errore o nomi delle chiavi mancanti), al client il messaggio generico
			log.error("Caricamento della foto profilo non riuscito: {}", e.getMessage());
			throw new ApplicazioneException(CodiceErrore.ERRORE_INTERNO, MSG_ERRORE_INTERNO);
		}
		utenteRepository.aggiornaFotoProfilo(utenteId, url);
		return new AvatarResponse(url);
	}

	/**
	 * Tipo dell'immagine riconosciuto dai primi byte ("magic number"), non dall'estensione o dal content-type
	 * dichiarato, che il client può scrivere come vuole: JPEG FF D8 FF, PNG 89 'PNG' 0D 0A 1A 0A,
	 * WEBP "RIFF" + 4 byte di lunghezza + "WEBP".
	 */
	static Optional<String> tipoImmagine(byte[] contenuto) {
		if (iniziaCon(contenuto, 0, JPEG)) {
			return Optional.of("image/jpeg");
		}
		if (iniziaCon(contenuto, 0, PNG)) {
			return Optional.of("image/png");
		}
		if (iniziaCon(contenuto, 0, RIFF) && iniziaCon(contenuto, 8, WEBP)) {
			return Optional.of("image/webp");
		}
		return Optional.empty();
	}

	private static boolean iniziaCon(byte[] contenuto, int da, byte[] firma) {
		return contenuto.length >= da + firma.length
				&& Arrays.equals(contenuto, da, da + firma.length, firma, 0, firma.length);
	}

	private static ApplicazioneException fileNonValido() {
		return new ApplicazioneException(CodiceErrore.FILE_NON_VALIDO, MSG_FILE_NON_VALIDO);
	}

}
