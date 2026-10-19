package it.quackfit.tentativi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import it.quackfit.common.ClockConfig;
import it.quackfit.utente.Streak;
import it.quackfit.utente.Utente;

/**
 * Unit test della streak dopo una lezione superata (tentativi.md, Chiusura), con un orologio fisso: oggi invariata,
 * ieri +1, altrimenti 1; record = max(record, valore).
 */
class AggiornaStreakTest {

	/** 22:30 UTC del 14 ottobre: a Roma (ora legale) è già il 15, e il giorno si legge nel fuso di Roma. */
	private static final Clock OROLOGIO = Clock.fixed(Instant.parse("2026-10-14T22:30:00Z"), ClockConfig.FUSO);
	private static final Instant ADESSO = Instant.now(OROLOGIO);
	private static final LocalDate OGGI = LocalDate.ofInstant(ADESSO, ClockConfig.FUSO);
	private static final Instant CREAZIONE = Instant.parse("2026-01-01T10:00:00Z");

	private static Streak streak(int valore, int record, LocalDate ultimaAttivita) {
		Streak streak = new Streak(new Utente("Mario", "Rossi", "mario@example.com", "hash", CREAZIONE), CREAZIONE);
		streak.setValore(valore);
		streak.setRecord(record);
		streak.setUltimaAttivita(ultimaAttivita);
		return streak;
	}

	@Test
	void ilGiornoEQuelloDiRoma() {
		// 22:30 UTC del 14 sono le 00:30 del 15 a Roma
		assertThat(OGGI).isEqualTo(LocalDate.parse("2026-10-15"));
	}

	@Test
	void primoCompletamentoPortaA1() {
		Streak streak = streak(0, 0, null);

		TentativiService.aggiornaStreak(streak, OGGI, ADESSO);

		assertThat(streak.getValore()).isEqualTo(1);
		assertThat(streak.getRecord()).isEqualTo(1);
		assertThat(streak.getUltimaAttivita()).isEqualTo(OGGI);
		assertThat(streak.getUpdatedAt()).isEqualTo(ADESSO);
	}

	@Test
	void stessoGiornoInvariata() {
		Streak streak = streak(3, 5, OGGI);

		TentativiService.aggiornaStreak(streak, OGGI, ADESSO);

		assertThat(streak.getValore()).isEqualTo(3);
		assertThat(streak.getRecord()).isEqualTo(5);
		assertThat(streak.getUltimaAttivita()).isEqualTo(OGGI);
		assertThat(streak.getUpdatedAt()).isEqualTo(CREAZIONE);
	}

	@Test
	void giornoDopoPiuUnoSenzaSuperareIlRecord() {
		Streak streak = streak(3, 5, OGGI.minusDays(1));

		TentativiService.aggiornaStreak(streak, OGGI, ADESSO);

		assertThat(streak.getValore()).isEqualTo(4);
		assertThat(streak.getRecord()).isEqualTo(5);
		assertThat(streak.getUltimaAttivita()).isEqualTo(OGGI);
	}

	@Test
	void giornoDopoAggiornaIlRecord() {
		Streak streak = streak(5, 5, OGGI.minusDays(1));

		TentativiService.aggiornaStreak(streak, OGGI, ADESSO);

		assertThat(streak.getValore()).isEqualTo(6);
		assertThat(streak.getRecord()).isEqualTo(6);
	}

	@Test
	void giornoSaltatoRiparteDa1EIlRecordResta() {
		Streak streak = streak(7, 9, OGGI.minusDays(2));

		TentativiService.aggiornaStreak(streak, OGGI, ADESSO);

		assertThat(streak.getValore()).isEqualTo(1);
		assertThat(streak.getRecord()).isEqualTo(9);
		assertThat(streak.getUltimaAttivita()).isEqualTo(OGGI);
	}

}
