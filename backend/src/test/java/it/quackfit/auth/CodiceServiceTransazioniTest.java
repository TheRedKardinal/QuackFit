package it.quackfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.mail.MailService;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/**
 * Test di integrazione delle transazioni di CodiceService, con H2.
 *
 * Niente @Transactional sulla classe: i dati del test devono essere già salvati, altrimenti la transazione
 * interna di verifica (REQUIRES_NEW) non li vedrebbe. CodiceService è chiamato come bean di Spring,
 * quindi passando dal proxy: è così che lo chiameranno gli altri service.
 */
@SpringBootTest
@ActiveProfiles("test")
class CodiceServiceTransazioniTest {

	@Autowired
	private CodiceService codiceService;

	@Autowired
	private CodiceVerificaRepository codiceRepository;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	/** Finto: serve a leggere il codice in chiaro che il servizio "invia". */
	@MockitoBean
	private MailService mailService;

	private Utente utente;

	@BeforeEach
	void setUp() {
		pulisci();
		utente = utenteRepository.save(
				new Utente("Mario", "Rossi", "mario@example.com", "hash", Instant.parse("2026-01-01T10:00:00Z")));
	}

	@AfterEach
	void pulisci() {
		// prima i codici: hanno la chiave esterna verso l'utente
		codiceRepository.deleteAll();
		utenteRepository.deleteAll();
	}

	/** Genera un codice di verifica e restituisce quello in chiaro "inviato" per email. */
	private String generaCodice() {
		clearInvocations(mailService);
		codiceService.generaEInvia(utente, TipoCodice.VERIFICA_EMAIL, null);
		ArgumentCaptor<String> inviato = ArgumentCaptor.forClass(String.class);
		verify(mailService).inviaVerificaEmail(eq("mario@example.com"), eq("Mario"), inviato.capture());
		return inviato.getValue();
	}

	private CodiceVerifica unicoValidoDalDatabase() {
		return codiceRepository.findFirstByUtenteAndTipoAndUsatoAtIsNullOrderByCreatedAtDesc(utente, TipoCodice.VERIFICA_EMAIL)
				.orElseThrow();
	}

	private static String sbagliato(String giusto) {
		return giusto.equals("000000") ? "111111" : "000000";
	}

	@Test
	void ilTentativoSbagliatoRestaSalvatoNelDatabase() {
		String giusto = generaCodice();

		assertThatThrownBy(() -> codiceService.verifica(utente, TipoCodice.VERIFICA_EMAIL, sbagliato(giusto)))
				.isInstanceOf(ApplicazioneException.class);

		assertThat(unicoValidoDalDatabase().getTentativiFalliti()).isEqualTo(1);
	}

	@Test
	void ilTentativoSbagliatoRestaSalvatoAncheSeIlChiamanteAnnullaLaSuaTransazione() {
		String giusto = generaCodice();

		// come un service @Transactional che chiama verifica e poi fa rollback per l'eccezione
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
				stato -> codiceService.verifica(utente, TipoCodice.VERIFICA_EMAIL, sbagliato(giusto))))
				.isInstanceOf(ApplicazioneException.class);

		assertThat(unicoValidoDalDatabase().getTentativiFalliti()).isEqualTo(1);
	}

	@Test
	void unCodiceGiustoRestaUsatoAncheSeIlChiamanteFallisceDopo() {
		String giusto = generaCodice();

		// come la conferma del cambio email che, dopo la verifica, risponde 409
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(stato -> {
			codiceService.verifica(utente, TipoCodice.VERIFICA_EMAIL, giusto);
			throw new IllegalStateException("errore del chiamante dopo la verifica");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(codiceRepository.findAll()).singleElement()
				.satisfies(codice -> assertThat(codice.getUsatoAt()).isNotNull());
	}

	@Test
	void unCodiceVecchioEInvalidatoDaUnoNuovo() {
		String vecchio = generaCodice();
		// il primo codice "risale" a due minuti fa, così il limite dei 60 secondi non blocca il secondo
		CodiceVerifica primo = unicoValidoDalDatabase();
		primo.setCreatedAt(primo.getCreatedAt().minus(Duration.ofMinutes(2)));
		codiceRepository.save(primo);
		String nuovo = generaCodice();

		if (!vecchio.equals(nuovo)) {
			assertThatThrownBy(() -> codiceService.verifica(utente, TipoCodice.VERIFICA_EMAIL, vecchio))
					.isInstanceOf(ApplicazioneException.class);
		}
		codiceService.verifica(utente, TipoCodice.VERIFICA_EMAIL, nuovo);

		assertThat(codiceRepository.findById(primo.getId()).orElseThrow().getUsatoAt()).isNotNull();
	}

	@Test
	void reinvioEntro60SecondiRifiutato() {
		generaCodice();

		assertThatThrownBy(() -> codiceService.generaEInvia(utente, TipoCodice.VERIFICA_EMAIL, null))
				.isInstanceOf(ApplicazioneException.class)
				.hasMessage("Attendi un minuto prima di richiedere un nuovo codice");
		assertThat(codiceRepository.count()).isEqualTo(1);
		verify(mailService).inviaVerificaEmail(eq("mario@example.com"), eq("Mario"), anyString());
	}

}
