package it.quackfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.ClockConfig;
import it.quackfit.common.CodiceErrore;
import it.quackfit.security.JwtService;
import it.quackfit.utente.Ruolo;
import it.quackfit.utente.Streak;
import it.quackfit.utente.StreakRepository;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;

/** Unit test della registrazione con Mockito e Clock fisso. */
class AuthServiceTest {

	private static final Instant ADESSO = Instant.parse("2026-10-16T10:00:00Z");
	private static final RegistrazioneRequest RICHIESTA =
			new RegistrazioneRequest("Mario", "Rossi", "mario@example.com", "Password1!");

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

	private UtenteRepository utenteRepository;
	private StreakRepository streakRepository;
	private CodiceService codiceService;
	private JwtService jwtService;
	private AuthService servizio;

	@BeforeEach
	void setUp() {
		utenteRepository = mock(UtenteRepository.class);
		streakRepository = mock(StreakRepository.class);
		codiceService = mock(CodiceService.class);
		jwtService = mock(JwtService.class);
		servizio = new AuthService(utenteRepository, streakRepository, codiceService, encoder, jwtService,
				Clock.fixed(ADESSO, ClockConfig.FUSO));
		// il database assegna l'id al salvataggio
		when(utenteRepository.saveAndFlush(any(Utente.class))).thenAnswer(invocazione -> {
			Utente utente = invocazione.getArgument(0);
			ReflectionTestUtils.setField(utente, "id", UUID.randomUUID());
			return utente;
		});
		when(jwtService.genera(any(Utente.class))).thenReturn("token-di-prova");
	}

	private static void emailGiaUsata(Runnable azione) {
		assertThatThrownBy(azione::run)
				.isInstanceOf(ApplicazioneException.class)
				.hasMessage("Email già registrata. Se è tua, accedi oppure usa 'Password dimenticata'.")
				.satisfies(e -> assertThat(((ApplicazioneException) e).getCodice()).isEqualTo(CodiceErrore.EMAIL_GIA_USATA));
	}

	@Test
	void creaLUtenteConIValoriIniziali() {
		servizio.registra(RICHIESTA);

		ArgumentCaptor<Utente> salvato = ArgumentCaptor.forClass(Utente.class);
		verify(utenteRepository).saveAndFlush(salvato.capture());
		Utente utente = salvato.getValue();
		assertThat(utente.getNome()).isEqualTo("Mario");
		assertThat(utente.getCognome()).isEqualTo("Rossi");
		assertThat(utente.getEmail()).isEqualTo("mario@example.com");
		assertThat(utente.getRuolo()).isEqualTo(Ruolo.UTENTE);
		assertThat(utente.isEmailVerificata()).isFalse();
		assertThat(utente.getTokenValidiDal()).isEqualTo(ADESSO);
		assertThat(utente.getCreatedAt()).isEqualTo(ADESSO);
		assertThat(utente.getLoginFalliti()).isZero();
		assertThat(utente.getBloccatoFino()).isNull();
	}

	@Test
	void laPasswordSiSalvaSoloComeHash() {
		servizio.registra(RICHIESTA);

		ArgumentCaptor<Utente> salvato = ArgumentCaptor.forClass(Utente.class);
		verify(utenteRepository).saveAndFlush(salvato.capture());
		String hash = salvato.getValue().getPassword();
		assertThat(hash).isNotEqualTo("Password1!").startsWith("$2");
		assertThat(encoder.matches("Password1!", hash)).isTrue();
	}

	@Test
	void creaLaStreakAZero() {
		servizio.registra(RICHIESTA);

		ArgumentCaptor<Streak> salvata = ArgumentCaptor.forClass(Streak.class);
		verify(streakRepository).save(salvata.capture());
		Streak streak = salvata.getValue();
		assertThat(streak.getValore()).isZero();
		assertThat(streak.getRecord()).isZero();
		assertThat(streak.getUltimaAttivita()).isNull();
		assertThat(streak.getUtente().getEmail()).isEqualTo("mario@example.com");
	}

	@Test
	void inviaIlCodiceDiVerificaERestituisceIlToken() {
		TokenResponse risposta = servizio.registra(RICHIESTA);

		ArgumentCaptor<Utente> utente = ArgumentCaptor.forClass(Utente.class);
		verify(codiceService).generaEInvia(utente.capture(), eq(TipoCodice.VERIFICA_EMAIL),
				isNull());
		assertThat(utente.getValue().getId()).isNotNull();
		assertThat(risposta.token()).isEqualTo("token-di-prova");
	}

	@Test
	void emailGiaUsataNonSalvaNeInvia() {
		when(utenteRepository.existsByEmail("mario@example.com")).thenReturn(true);

		emailGiaUsata(() -> servizio.registra(RICHIESTA));

		verify(utenteRepository, never()).saveAndFlush(any());
		verifyNoInteractions(streakRepository, codiceService, jwtService);
	}

	@Test
	void registrazioneSimultaneaConLaStessaEmail409() {
		// il controllo dice "libera", ma intanto un'altra richiesta l'ha presa: scatta il vincolo UNIQUE
		when(utenteRepository.existsByEmail("mario@example.com")).thenReturn(false);
		when(utenteRepository.saveAndFlush(any(Utente.class)))
				.thenThrow(new DataIntegrityViolationException("unique constraint"));

		emailGiaUsata(() -> servizio.registra(RICHIESTA));

		verifyNoInteractions(streakRepository, codiceService, jwtService);
	}

}
