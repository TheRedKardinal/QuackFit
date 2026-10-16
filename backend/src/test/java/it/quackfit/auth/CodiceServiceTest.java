package it.quackfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.ClockConfig;
import it.quackfit.common.CodiceErrore;
import it.quackfit.mail.MailService;
import it.quackfit.utente.Utente;

/** Unit test di CodiceService con Mockito e Clock fisso: tutti i casi della sezione "Codici" di auth.md. */
class CodiceServiceTest {

	private static final Instant ADESSO = Instant.parse("2026-10-16T10:00:00Z");

	/** Forza bassa: stessa logica di BCrypt, test più veloci. */
	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

	private CodiceVerificaRepository repository;
	private MailService mailService;
	private CodiceService servizio;
	private Utente utente;

	@BeforeEach
	void setUp() {
		repository = mock(CodiceVerificaRepository.class);
		mailService = mock(MailService.class);
		servizio = new CodiceService(repository, encoder, mailService, Clock.fixed(ADESSO, ClockConfig.FUSO));
		utente = new Utente("Mario", "Rossi", "mario@example.com", "hash", ADESSO.minus(Duration.ofDays(1)));
		ReflectionTestUtils.setField(utente, "id", UUID.randomUUID());
	}

	/** Un codice salvato nel database, creato all'istante indicato. */
	private CodiceVerifica codice(TipoCodice tipo, String inChiaro, Instant creato) {
		return new CodiceVerifica(utente, tipo, encoder.encode(inChiaro), null, creato);
	}

	private void ultimoValidoE(CodiceVerifica codice) {
		when(repository.findFirstByUtenteAndTipoAndUsatoAtIsNullOrderByCreatedAtDesc(utente, codice.getTipo()))
				.thenReturn(Optional.of(codice));
	}

	private static void erroreConCodice(Runnable azione, CodiceErrore atteso) {
		assertThatThrownBy(azione::run)
				.isInstanceOf(ApplicazioneException.class)
				.satisfies(e -> assertThat(((ApplicazioneException) e).getCodice()).isEqualTo(atteso));
	}

	// --- generazione e invio ---

	@Test
	void generaSeiCifreSalvaSoloLHashEInviaLEmailDiVerifica() {
		servizio.generaEInvia(utente, TipoCodice.VERIFICA_EMAIL, null);

		ArgumentCaptor<CodiceVerifica> salvato = ArgumentCaptor.forClass(CodiceVerifica.class);
		verify(repository).save(salvato.capture());
		ArgumentCaptor<String> inviato = ArgumentCaptor.forClass(String.class);
		verify(mailService).inviaVerificaEmail(eq("mario@example.com"), eq("Mario"), inviato.capture());

		String inChiaro = inviato.getValue();
		CodiceVerifica codice = salvato.getValue();
		assertThat(inChiaro).matches("\\d{6}");
		assertThat(codice.getCodiceHash()).isNotEqualTo(inChiaro).startsWith("$2");
		assertThat(encoder.matches(inChiaro, codice.getCodiceHash())).isTrue();
		assertThat(codice.getTipo()).isEqualTo(TipoCodice.VERIFICA_EMAIL);
		assertThat(codice.getCreatedAt()).isEqualTo(ADESSO);
		assertThat(codice.getScadenza()).isEqualTo(ADESSO.plus(Duration.ofMinutes(15)));
		assertThat(codice.getTentativiFalliti()).isZero();
		assertThat(codice.getUsatoAt()).isNull();
		assertThat(codice.getNuovaEmail()).isNull();
	}

	@Test
	void resetPasswordVaAllEmailDellUtente() {
		servizio.generaEInvia(utente, TipoCodice.RESET_PASSWORD, null);

		verify(mailService).inviaResetPassword(eq("mario@example.com"), eq("Mario"), anyString());
	}

	@Test
	void cambioEmailSalvaLaNuovaEmailEScriveAQuella() {
		servizio.generaEInvia(utente, TipoCodice.CAMBIO_EMAIL, "nuova@example.com");

		ArgumentCaptor<CodiceVerifica> salvato = ArgumentCaptor.forClass(CodiceVerifica.class);
		verify(repository).save(salvato.capture());
		assertThat(salvato.getValue().getNuovaEmail()).isEqualTo("nuova@example.com");
		verify(mailService).inviaConfermaCambioEmail(eq("nuova@example.com"), eq("Mario"), anyString());
	}

	@Test
	void nuovaEmailSoloPerIlCambioEmail() {
		assertThatThrownBy(() -> servizio.generaEInvia(utente, TipoCodice.CAMBIO_EMAIL, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> servizio.generaEInvia(utente, TipoCodice.VERIFICA_EMAIL, "x@example.com"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unNuovoCodiceInvalidaIPrecedentiDelloStessoTipo() {
		CodiceVerifica primo = codice(TipoCodice.VERIFICA_EMAIL, "111111", ADESSO.minus(Duration.ofMinutes(5)));
		CodiceVerifica secondo = codice(TipoCodice.VERIFICA_EMAIL, "222222", ADESSO.minus(Duration.ofMinutes(2)));
		when(repository.findByUtenteAndTipoAndUsatoAtIsNull(utente, TipoCodice.VERIFICA_EMAIL))
				.thenReturn(List.of(primo, secondo));

		servizio.generaEInvia(utente, TipoCodice.VERIFICA_EMAIL, null);

		assertThat(primo.getUsatoAt()).isEqualTo(ADESSO);
		assertThat(secondo.getUsatoAt()).isEqualTo(ADESSO);
	}

	@Test
	void reinvioPrimaDi60Secondi429SenzaSalvareNeInviare() {
		when(repository.findFirstByUtenteAndTipoOrderByCreatedAtDesc(utente, TipoCodice.VERIFICA_EMAIL))
				.thenReturn(Optional.of(codice(TipoCodice.VERIFICA_EMAIL, "111111", ADESSO.minusSeconds(59))));

		erroreConCodice(() -> servizio.generaEInvia(utente, TipoCodice.VERIFICA_EMAIL, null), CodiceErrore.ATTENDI_REINVIO);

		verify(repository, never()).save(any());
		verifyNoInteractions(mailService);
	}

	@Test
	void reinvioDopo60SecondiAmmesso() {
		when(repository.findFirstByUtenteAndTipoOrderByCreatedAtDesc(utente, TipoCodice.VERIFICA_EMAIL))
				.thenReturn(Optional.of(codice(TipoCodice.VERIFICA_EMAIL, "111111", ADESSO.minusSeconds(60))));

		servizio.generaEInvia(utente, TipoCodice.VERIFICA_EMAIL, null);

		verify(repository).save(any());
	}

	@Test
	void reinvioConsentito() {
		assertThat(servizio.reinvioConsentito(utente, TipoCodice.RESET_PASSWORD)).as("nessun codice").isTrue();

		when(repository.findFirstByUtenteAndTipoOrderByCreatedAtDesc(utente, TipoCodice.RESET_PASSWORD))
				.thenReturn(Optional.of(codice(TipoCodice.RESET_PASSWORD, "111111", ADESSO.minusSeconds(30))));
		assertThat(servizio.reinvioConsentito(utente, TipoCodice.RESET_PASSWORD)).as("30 secondi fa").isFalse();

		when(repository.findFirstByUtenteAndTipoOrderByCreatedAtDesc(utente, TipoCodice.RESET_PASSWORD))
				.thenReturn(Optional.of(codice(TipoCodice.RESET_PASSWORD, "111111", ADESSO.minusSeconds(61))));
		assertThat(servizio.reinvioConsentito(utente, TipoCodice.RESET_PASSWORD)).as("61 secondi fa").isTrue();
	}

	@Test
	void ilCodiceHaSempreSeiCifre() {
		assertThat(CodiceService.seiCifre(4213)).isEqualTo("004213");
		assertThat(CodiceService.seiCifre(0)).isEqualTo("000000");
		assertThat(CodiceService.seiCifre(999_999)).isEqualTo("999999");
	}

	// --- verifica ---

	@Test
	void codiceGiustoVieneMarcatoUsato() {
		CodiceVerifica codice = codice(TipoCodice.VERIFICA_EMAIL, "123456", ADESSO.minus(Duration.ofMinutes(10)));
		ultimoValidoE(codice);

		CodiceVerificato esito = servizio.verifica(utente, TipoCodice.VERIFICA_EMAIL, "123456");

		assertThat(codice.getUsatoAt()).isEqualTo(ADESSO);
		assertThat(esito.nuovaEmail()).isNull();
	}

	@Test
	void cambioEmailRestituisceLaNuovaEmail() {
		CodiceVerifica codice = new CodiceVerifica(utente, TipoCodice.CAMBIO_EMAIL, encoder.encode("123456"),
				"nuova@example.com", ADESSO.minus(Duration.ofMinutes(1)));
		ultimoValidoE(codice);

		assertThat(servizio.verifica(utente, TipoCodice.CAMBIO_EMAIL, "123456").nuovaEmail())
				.isEqualTo("nuova@example.com");
	}

	@Test
	void nessunCodiceValidoNonValido() {
		erroreConCodice(() -> servizio.verifica(utente, TipoCodice.VERIFICA_EMAIL, "123456"), CodiceErrore.CODICE_NON_VALIDO);
	}

	@Test
	void codiceScadutoAncheSeGiusto() {
		// creato 15 minuti fa: la scadenza è adesso
		CodiceVerifica codice = codice(TipoCodice.VERIFICA_EMAIL, "123456", ADESSO.minus(Duration.ofMinutes(15)));
		ultimoValidoE(codice);

		erroreConCodice(() -> servizio.verifica(utente, TipoCodice.VERIFICA_EMAIL, "123456"), CodiceErrore.CODICE_SCADUTO);
		assertThat(codice.getUsatoAt()).isNull();
		assertThat(codice.getTentativiFalliti()).isZero();
	}

	@Test
	void giaCinqueTentativiTroppiAncheConIlCodiceGiusto() {
		CodiceVerifica codice = codice(TipoCodice.VERIFICA_EMAIL, "123456", ADESSO.minus(Duration.ofMinutes(1)));
		codice.setTentativiFalliti(5);
		ultimoValidoE(codice);

		erroreConCodice(() -> servizio.verifica(utente, TipoCodice.VERIFICA_EMAIL, "123456"), CodiceErrore.TROPPI_TENTATIVI);
		assertThat(codice.getUsatoAt()).isNull();
	}

	@Test
	void codiceSbagliatoContaUnTentativo() {
		CodiceVerifica codice = codice(TipoCodice.VERIFICA_EMAIL, "123456", ADESSO.minus(Duration.ofMinutes(1)));
		ultimoValidoE(codice);

		erroreConCodice(() -> servizio.verifica(utente, TipoCodice.VERIFICA_EMAIL, "000000"), CodiceErrore.CODICE_NON_VALIDO);
		assertThat(codice.getTentativiFalliti()).isEqualTo(1);
		assertThat(codice.getUsatoAt()).isNull();
	}

	@Test
	void ilQuintoErroreRispondeGiaTroppiTentativi() {
		CodiceVerifica codice = codice(TipoCodice.VERIFICA_EMAIL, "123456", ADESSO.minus(Duration.ofMinutes(1)));
		ultimoValidoE(codice);

		for (int i = 1; i <= 4; i++) {
			erroreConCodice(() -> servizio.verifica(utente, TipoCodice.VERIFICA_EMAIL, "000000"),
					CodiceErrore.CODICE_NON_VALIDO);
		}
		erroreConCodice(() -> servizio.verifica(utente, TipoCodice.VERIFICA_EMAIL, "000000"), CodiceErrore.TROPPI_TENTATIVI);

		assertThat(codice.getTentativiFalliti()).isEqualTo(5);
		// da qui in poi anche il codice giusto è rifiutato
		erroreConCodice(() -> servizio.verifica(utente, TipoCodice.VERIFICA_EMAIL, "123456"), CodiceErrore.TROPPI_TENTATIVI);
	}

	@Test
	void codiceNulloContaComeSbagliato() {
		CodiceVerifica codice = codice(TipoCodice.VERIFICA_EMAIL, "123456", ADESSO.minus(Duration.ofMinutes(1)));
		ultimoValidoE(codice);

		erroreConCodice(() -> servizio.verifica(utente, TipoCodice.VERIFICA_EMAIL, null), CodiceErrore.CODICE_NON_VALIDO);
		assertThat(codice.getTentativiFalliti()).isEqualTo(1);
	}

	@Test
	void messaggiDellaSpec() {
		ultimoValidoE(codice(TipoCodice.VERIFICA_EMAIL, "123456", ADESSO.minus(Duration.ofMinutes(15))));

		assertThatThrownBy(() -> servizio.verifica(utente, TipoCodice.VERIFICA_EMAIL, "123456"))
				.hasMessage("Codice scaduto: richiedine uno nuovo");
		assertThatThrownBy(() -> servizio.verifica(utente, TipoCodice.RESET_PASSWORD, "123456"))
				.hasMessage("Codice non valido");
	}

	// --- confronto fittizio: ogni verifica fa un confronto BCrypt, così i tempi non cambiano ---

	/** Servizio con un encoder "spia", per contare i confronti BCrypt. */
	private CodiceService servizioConSpia(PasswordEncoder spia) {
		CodiceService conSpia = new CodiceService(repository, spia, mailService, Clock.fixed(ADESSO, ClockConfig.FUSO));
		clearInvocations(spia); // l'encode dell'hash fittizio fatto nel costruttore non conta
		return conSpia;
	}

	@Test
	void ancheSenzaCodiceDaConfrontareSiFaUnConfronto() {
		PasswordEncoder spia = spy(encoder);
		CodiceService conSpia = servizioConSpia(spia);

		// 1. nessun codice valido
		erroreConCodice(() -> conSpia.verifica(utente, TipoCodice.RESET_PASSWORD, "123456"), CodiceErrore.CODICE_NON_VALIDO);
		verify(spia).matches(eq("123456"), anyString());

		// 2. codice scaduto
		clearInvocations(spia);
		ultimoValidoE(codice(TipoCodice.RESET_PASSWORD, "123456", ADESSO.minus(Duration.ofMinutes(20))));
		erroreConCodice(() -> conSpia.verifica(utente, TipoCodice.RESET_PASSWORD, "123456"), CodiceErrore.CODICE_SCADUTO);
		verify(spia).matches(eq("123456"), anyString());

		// 3. tentativi esauriti
		clearInvocations(spia);
		CodiceVerifica bloccato = codice(TipoCodice.RESET_PASSWORD, "123456", ADESSO.minus(Duration.ofMinutes(1)));
		bloccato.setTentativiFalliti(CodiceService.MASSIMO_TENTATIVI);
		ultimoValidoE(bloccato);
		erroreConCodice(() -> conSpia.verifica(utente, TipoCodice.RESET_PASSWORD, "123456"), CodiceErrore.TROPPI_TENTATIVI);
		verify(spia).matches(eq("123456"), anyString());
	}

	@Test
	void confrontoFittizio() {
		PasswordEncoder spia = spy(encoder);
		CodiceService conSpia = servizioConSpia(spia);

		conSpia.confrontoFittizio("123456");
		verify(spia).matches(eq("123456"), anyString());

		clearInvocations(spia);
		conSpia.confrontoFittizio(null);
		verify(spia, never()).matches(any(), any());
	}

}
