package it.quackfit.utente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import it.quackfit.auth.CodiceVerificaRepository;
import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;

/** Unit test di CancellazioneAccountService con Mockito, un orologio fisso e un BCrypt vero ma veloce (costo 4). */
class CancellazioneAccountServiceTest {

	private static final Instant ADESSO = Instant.parse("2026-10-17T10:00:00Z");
	private static final String PASSWORD = "Password1!";
	private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

	private UtenteRepository utenteRepository;
	private StreakRepository streakRepository;
	private CodiceVerificaRepository codiceRepository;
	private ApplicationEventPublisher eventi;
	private CancellazioneAccountService servizio;
	private Utente utente;

	@BeforeEach
	void setUp() {
		utenteRepository = mock(UtenteRepository.class);
		streakRepository = mock(StreakRepository.class);
		codiceRepository = mock(CodiceVerificaRepository.class);
		eventi = mock(ApplicationEventPublisher.class);
		servizio = new CancellazioneAccountService(utenteRepository, streakRepository, codiceRepository,
				new PasswordAttuale(ENCODER), ENCODER, eventi, Clock.fixed(ADESSO, ZoneOffset.UTC));
		utente = new Utente("Mario", "Rossi", "mario@example.com", ENCODER.encode(PASSWORD),
				Instant.parse("2026-01-01T10:00:00Z"));
		utente.setEmailVerificata(true);
		ReflectionTestUtils.setField(utente, "id", UUID.randomUUID());
		when(utenteRepository.findById(utente.getId())).thenReturn(Optional.of(utente));
	}

	private void cancella(String password) {
		servizio.cancella(utente.getId(), new CancellazioneAccountRequest(password));
	}

	@Test
	void anonimizzaELogoutOvunque() {
		String hashPrima = utente.getPassword();

		cancella(PASSWORD);

		assertThat(utente.getNome()).isEqualTo("Utente");
		assertThat(utente.getCognome()).isEqualTo("Eliminato");
		assertThat(utente.getEmail()).isEqualTo("deleted-" + utente.getId() + "@quackfit.local");
		assertThat(utente.getPassword()).isNotEqualTo(hashPrima);
		assertThat(ENCODER.matches(PASSWORD, utente.getPassword())).isFalse();
		assertThat(utente.getFotoProfilo()).isNull();
		assertThat(utente.getDeletedAt()).isEqualTo(ADESSO);
		assertThat(utente.getTokenValidiDal()).isEqualTo(ADESSO);
	}

	@Test
	void eliminaStreakECodici() {
		cancella(PASSWORD);

		verify(streakRepository).deleteByUtente(utente);
		verify(codiceRepository).deleteByUtente(utente);
	}

	@Test
	void conLaFotoChiedeDiEliminarlaConLIdFisso() {
		utente.setFotoProfilo("https://res.cloudinary.com/q/image/upload/v1/avatar/x.png");

		cancella(PASSWORD);

		verify(eventi).publishEvent(new FotoDaEliminare("avatar/" + utente.getId()));
	}

	@Test
	void senzaFotoNessunaEliminazione() {
		cancella(PASSWORD);

		verifyNoInteractions(eventi);
	}

	@Test
	void passwordSbagliataNessunaModifica() {
		assertThatThrownBy(() -> cancella("Sbagliata1!"))
				.isInstanceOfSatisfying(ApplicazioneException.class,
						e -> assertThat(e.getCodice()).isEqualTo(CodiceErrore.PASSWORD_ATTUALE_ERRATA));
		assertThat(utente.getDeletedAt()).isNull();
		assertThat(utente.getEmail()).isEqualTo("mario@example.com");
		verify(streakRepository, never()).deleteByUtente(any());
	}

	@Test
	void ilSuperadminNonPuoCancellarsi() {
		utente.setRuolo(Ruolo.SUPERADMIN);

		assertThatThrownBy(() -> cancella(PASSWORD))
				.isInstanceOfSatisfying(ApplicazioneException.class,
						e -> assertThat(e.getCodice()).isEqualTo(CodiceErrore.ACCESSO_NEGATO));
		assertThat(utente.getDeletedAt()).isNull();
	}

	@Test
	void unAdminPuoCancellarsi() {
		utente.setRuolo(Ruolo.ADMIN);

		cancella(PASSWORD);

		assertThat(utente.getDeletedAt()).isEqualTo(ADESSO);
	}

}
