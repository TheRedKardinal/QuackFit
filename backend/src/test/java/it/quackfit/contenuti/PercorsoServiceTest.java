package it.quackfit.contenuti;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** Unit test del calcolo degli stati (percorso.md, Stato delle lezioni), senza Spring. */
class PercorsoServiceTest {

	/** Un percorso di n lezioni, già nell'ordine (tappa, poi lezione). */
	private static List<UUID> percorso(int n) {
		return IntStream.range(0, n).mapToObj(i -> UUID.randomUUID()).toList();
	}

	private static List<StatoLezione> stati(List<UUID> percorso, Set<UUID> completate) {
		Map<UUID, StatoLezione> stati = PercorsoService.calcolaStati(percorso, completate);
		return percorso.stream().map(stati::get).toList();
	}

	@Test
	void nuovoUtenteSoloLaPrimaSbloccata() {
		List<UUID> lezioni = percorso(4);

		assertThat(stati(lezioni, Set.of())).containsExactly(StatoLezione.SBLOCCATA, StatoLezione.BLOCCATA,
				StatoLezione.BLOCCATA, StatoLezione.BLOCCATA);
	}

	@Test
	void completareUnaLezioneSbloccaLaSuccessiva() {
		List<UUID> lezioni = percorso(4);

		assertThat(stati(lezioni, Set.of(lezioni.get(0), lezioni.get(1)))).containsExactly(StatoLezione.COMPLETATA,
				StatoLezione.COMPLETATA, StatoLezione.SBLOCCATA, StatoLezione.BLOCCATA);
	}

	@Test
	void tuttoCompletato() {
		List<UUID> lezioni = percorso(3);

		assertThat(stati(lezioni, Set.copyOf(lezioni))).containsOnly(StatoLezione.COMPLETATA);
	}

	@Test
	void lezioneNuovaInMezzoRisultaSbloccataAncheSeLeSuccessiveSonoCompletate() {
		// conseguenza voluta di percorso.md: l'admin inserisce (o sposta) una lezione prima di altre già completate
		List<UUID> lezioni = percorso(4);
		Set<UUID> completate = Set.of(lezioni.get(0), lezioni.get(2), lezioni.get(3));

		assertThat(stati(lezioni, completate)).containsExactly(StatoLezione.COMPLETATA, StatoLezione.SBLOCCATA,
				StatoLezione.COMPLETATA, StatoLezione.COMPLETATA);
	}

	@Test
	void unaLezioneDopoUnaNonCompletataEBloccata() {
		List<UUID> lezioni = percorso(3);

		assertThat(stati(lezioni, Set.of(lezioni.get(1)))).containsExactly(StatoLezione.SBLOCCATA,
				StatoLezione.COMPLETATA, StatoLezione.SBLOCCATA);
	}

	@Test
	void percorsoVuoto() {
		assertThat(PercorsoService.calcolaStati(List.of(), Set.of())).isEmpty();
	}

}
