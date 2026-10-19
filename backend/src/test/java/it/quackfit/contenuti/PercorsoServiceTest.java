package it.quackfit.contenuti;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import it.quackfit.contenuti.LezioneContenutoResponse.DomandaLezioneResponse;
import it.quackfit.contenuti.LezioneContenutoResponse.VoceResponse;

/** Unit test del calcolo degli stati e delle domande per chi studia (percorso.md), senza Spring. */
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

	// --- domande per chi studia ---

	/** Una domanda con id finti su domanda, opzioni ed elementi (in un test unitario nessuno li genera). */
	private static Domanda conId(Domanda domanda) {
		ReflectionTestUtils.setField(domanda, "id", UUID.randomUUID());
		domanda.getOpzioni().forEach(o -> ReflectionTestUtils.setField(o, "id", UUID.randomUUID()));
		domanda.getElementi().forEach(e -> ReflectionTestUtils.setField(e, "id", UUID.randomUUID()));
		return domanda;
	}

	private static List<String> testi(List<VoceResponse> voci) {
		return voci.stream().map(VoceResponse::testo).toList();
	}

	@Test
	void veroFalsoSempreNellOrdineDellAdminEListeDellAbbinamentoVuote() {
		Domanda domanda = new Domanda(null, "Testo", "Spiegazione", TipoDomanda.VERO_FALSO, 1, Instant.EPOCH);
		domanda.aggiungiOpzione("Vero", false);
		domanda.aggiungiOpzione("Falso", true);
		conId(domanda);

		for (int seme = 0; seme < 20; seme++) {
			DomandaLezioneResponse risposta = PercorsoService.domanda(domanda, new Random(seme));
			assertThat(testi(risposta.opzioni())).containsExactly("Vero", "Falso");
			assertThat(risposta.sinistra()).isEmpty();
			assertThat(risposta.destra()).isEmpty();
		}
	}

	@Test
	void sceltaMultiplaTutteLeOpzioniMescolate() {
		Domanda domanda = new Domanda(null, "Testo", "Spiegazione", TipoDomanda.SCELTA_MULTIPLA, 1, Instant.EPOCH);
		for (String testo : List.of("A", "B", "C", "D", "E")) {
			domanda.aggiungiOpzione(testo, testo.equals("A"));
		}
		conId(domanda);

		DomandaLezioneResponse risposta = PercorsoService.domanda(domanda, new Random(1));

		assertThat(testi(risposta.opzioni())).containsExactlyInAnyOrder("A", "B", "C", "D", "E")
				.isNotEqualTo(List.of("A", "B", "C", "D", "E"));
		assertThat(risposta.sinistra()).isEmpty();
	}

	@Test
	void abbinamentoDestraNonNellOrdineDelleCoppie() {
		Domanda domanda = new Domanda(null, "Testo", "Spiegazione", TipoDomanda.ABBINAMENTO, 1, Instant.EPOCH);
		domanda.aggiungiCoppia("S1", "D1");
		domanda.aggiungiCoppia("S2", "D2");
		domanda.aggiungiCoppia("S3", "D3");
		domanda.aggiungiCoppia("S4", "D4");
		conId(domanda);

		DomandaLezioneResponse risposta = PercorsoService.domanda(domanda, new Random(1));

		assertThat(risposta.opzioni()).isEmpty();
		assertThat(testi(risposta.sinistra())).containsExactlyInAnyOrder("S1", "S2", "S3", "S4");
		assertThat(testi(risposta.destra())).containsExactlyInAnyOrder("D1", "D2", "D3", "D4");
		// la posizione non rivela le coppie: destra non è nell'ordine delle coppie rispetto a sinistra
		List<String> compagniDiSinistra = testi(risposta.sinistra()).stream().map(s -> "D" + s.substring(1)).toList();
		assertThat(testi(risposta.destra())).isNotEqualTo(compagniDiSinistra);
	}

}
