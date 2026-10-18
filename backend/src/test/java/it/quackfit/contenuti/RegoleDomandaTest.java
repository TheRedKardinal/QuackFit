package it.quackfit.contenuti;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import it.quackfit.contenuti.CreazioneDomandaRequest.CoppiaRequest;
import it.quackfit.contenuti.CreazioneDomandaRequest.OpzioneRequest;
import it.quackfit.contenuti.ModificaDomandaRequest.TestoRequest;

/** Unit test di ogni regola di RegoleDomanda (admin.md, Domande): un test per regola, senza Spring. */
class RegoleDomandaTest {

	// --- costruzione delle richieste ---

	private static CreazioneDomandaRequest veroFalso(Boolean rispostaVero, List<OpzioneRequest> opzioni,
			List<CoppiaRequest> coppie) {
		return new CreazioneDomandaRequest("Testo", "Spiegazione", TipoDomanda.VERO_FALSO, rispostaVero, opzioni, coppie);
	}

	private static CreazioneDomandaRequest multipla(List<OpzioneRequest> opzioni) {
		return new CreazioneDomandaRequest("Testo", "Spiegazione", TipoDomanda.SCELTA_MULTIPLA, null, opzioni, null);
	}

	private static CreazioneDomandaRequest abbinamento(List<CoppiaRequest> coppie) {
		return new CreazioneDomandaRequest("Testo", "Spiegazione", TipoDomanda.ABBINAMENTO, null, null, coppie);
	}

	/** n opzioni "A", "B", …; la prima è corretta. */
	private static List<OpzioneRequest> opzioni(int n) {
		return IntStream.range(0, n).mapToObj(i -> new OpzioneRequest(String.valueOf((char) ('A' + i)), i == 0)).toList();
	}

	/** n coppie "S1"/"D1", "S2"/"D2", …. */
	private static List<CoppiaRequest> coppie(int n) {
		return IntStream.rangeClosed(1, n).mapToObj(i -> new CoppiaRequest("S" + i, "D" + i)).toList();
	}

	@Nested
	class Creazione {

		@Test
		void veroFalsoValido() {
			assertThat(RegoleDomanda.creazione(veroFalso(true, null, null))).isEmpty();
			assertThat(RegoleDomanda.creazione(veroFalso(false, null, null))).isEmpty();
		}

		@Test
		void veroFalsoSenzaRispostaVero() {
			assertThat(RegoleDomanda.creazione(veroFalso(null, null, null)))
					.containsExactly(entry("rispostaVero", RegoleDomanda.MSG_RISPOSTA_VERO));
		}

		@Test
		void veroFalsoConCampiDiAltriTipi() {
			assertThat(RegoleDomanda.creazione(veroFalso(true, opzioni(2), coppie(2))))
					.containsEntry("opzioni", RegoleDomanda.MSG_NON_PREVISTO)
					.containsEntry("coppie", RegoleDomanda.MSG_NON_PREVISTO);
		}

		@ParameterizedTest(name = "{0} opzioni valide")
		@ValueSource(ints = { 2, 6 })
		void sceltaMultiplaValidaAiBordi(int n) {
			assertThat(RegoleDomanda.creazione(multipla(opzioni(n)))).isEmpty();
		}

		@ParameterizedTest(name = "{0} opzioni → 400")
		@ValueSource(ints = { 0, 1, 7 })
		void sceltaMultiplaConTroppeOPocheOpzioni(int n) {
			assertThat(RegoleDomanda.creazione(multipla(opzioni(n))))
					.containsExactly(entry("opzioni", RegoleDomanda.MSG_NUMERO_OPZIONI));
		}

		@Test
		void sceltaMultiplaSenzaOpzioni() {
			assertThat(RegoleDomanda.creazione(multipla(null)))
					.containsExactly(entry("opzioni", RegoleDomanda.MSG_NUMERO_OPZIONI));
		}

		@Test
		void sceltaMultiplaConZeroODueCorrette() {
			List<OpzioneRequest> nessuna = List.of(new OpzioneRequest("A", false), new OpzioneRequest("B", false));
			List<OpzioneRequest> due = List.of(new OpzioneRequest("A", true), new OpzioneRequest("B", true));

			assertThat(RegoleDomanda.creazione(multipla(nessuna)))
					.containsExactly(entry("opzioni", RegoleDomanda.MSG_UNA_CORRETTA));
			assertThat(RegoleDomanda.creazione(multipla(due)))
					.containsExactly(entry("opzioni", RegoleDomanda.MSG_UNA_CORRETTA));
		}

		@Test
		void sceltaMultiplaConCampiDiAltriTipi() {
			CreazioneDomandaRequest richiesta = new CreazioneDomandaRequest("Testo", "Spiegazione",
					TipoDomanda.SCELTA_MULTIPLA, false, opzioni(2), coppie(2));

			assertThat(RegoleDomanda.creazione(richiesta))
					.containsEntry("rispostaVero", RegoleDomanda.MSG_NON_PREVISTO)
					.containsEntry("coppie", RegoleDomanda.MSG_NON_PREVISTO);
		}

		@Test
		void opzioniRipetuteErroreSulSecondo() {
			List<OpzioneRequest> lista = List.of(new OpzioneRequest("Panca", true), new OpzioneRequest("Squat", false),
					new OpzioneRequest("Panca", false));

			assertThat(RegoleDomanda.creazione(multipla(lista)))
					.containsExactly(entry("opzioni[2].testo", RegoleDomanda.MSG_OPZIONE_UGUALE));
		}

		@Test
		void ripetutiSenzaMaiuscoleEConSpaziAiBordi() {
			// Il record toglie già gli spazi ai bordi: qui si prova la regola anche senza quella pulizia
			List<OpzioneRequest> opzioniUguali = List.of(new OpzioneRequest("Panca", true),
					new OpzioneRequest("  PANCA ", false));
			List<CoppiaRequest> coppieUguali = List.of(new CoppiaRequest("Bicipite", "Braccio"),
					new CoppiaRequest(" bicipite", "BRACCIO  "));

			assertThat(RegoleDomanda.creazione(multipla(opzioniUguali)))
					.containsExactly(entry("opzioni[1].testo", RegoleDomanda.MSG_OPZIONE_UGUALE));
			assertThat(RegoleDomanda.creazione(abbinamento(coppieUguali)))
					.containsEntry("coppie[1].sinistra", RegoleDomanda.MSG_SINISTRA_UGUALE)
					.containsEntry("coppie[1].destra", RegoleDomanda.MSG_DESTRA_UGUALE);
		}

		@ParameterizedTest(name = "{0} coppie valide")
		@ValueSource(ints = { 2, 6 })
		void abbinamentoValidoAiBordi(int n) {
			assertThat(RegoleDomanda.creazione(abbinamento(coppie(n)))).isEmpty();
		}

		@ParameterizedTest(name = "{0} coppie → 400")
		@ValueSource(ints = { 0, 1, 7 })
		void abbinamentoConTroppeOPocheCoppie(int n) {
			assertThat(RegoleDomanda.creazione(abbinamento(coppie(n))))
					.containsExactly(entry("coppie", RegoleDomanda.MSG_NUMERO_COPPIE));
			assertThat(RegoleDomanda.creazione(abbinamento(null)))
					.containsExactly(entry("coppie", RegoleDomanda.MSG_NUMERO_COPPIE));
		}

		@Test
		void abbinamentoConCampiDiAltriTipi() {
			CreazioneDomandaRequest richiesta = new CreazioneDomandaRequest("Testo", "Spiegazione",
					TipoDomanda.ABBINAMENTO, true, opzioni(2), coppie(2));

			assertThat(RegoleDomanda.creazione(richiesta))
					.containsEntry("rispostaVero", RegoleDomanda.MSG_NON_PREVISTO)
					.containsEntry("opzioni", RegoleDomanda.MSG_NON_PREVISTO);
		}

		@Test
		void sinistraRipetuta() {
			List<CoppiaRequest> lista = List.of(new CoppiaRequest("Bicipite", "Braccio"),
					new CoppiaRequest("Bicipite", "Coscia"));

			assertThat(RegoleDomanda.creazione(abbinamento(lista)))
					.containsExactly(entry("coppie[1].sinistra", RegoleDomanda.MSG_SINISTRA_UGUALE));
		}

		@Test
		void destraRipetuta() {
			List<CoppiaRequest> lista = List.of(new CoppiaRequest("Bicipite", "Braccio"),
					new CoppiaRequest("Tricipite", "Braccio"), new CoppiaRequest("Polpaccio", "Gamba"));

			assertThat(RegoleDomanda.creazione(abbinamento(lista)))
					.containsExactly(entry("coppie[1].destra", RegoleDomanda.MSG_DESTRA_UGUALE));
		}

		@Test
		void stessoTestoASinistraEADestraAmmesso() {
			List<CoppiaRequest> lista = List.of(new CoppiaRequest("Core", "Addome"), new CoppiaRequest("Addome", "Core"));

			assertThat(RegoleDomanda.creazione(abbinamento(lista))).isEmpty();
		}

	}

	@Nested
	class Modifica {

		private static Domanda domanda(TipoDomanda tipo) {
			Domanda domanda = new Domanda(null, "Testo", "Spiegazione", tipo, 1, Instant.EPOCH);
			switch (tipo) {
				case VERO_FALSO -> {
					domanda.aggiungiOpzione("Vero", true);
					domanda.aggiungiOpzione("Falso", false);
				}
				case SCELTA_MULTIPLA -> {
					domanda.aggiungiOpzione("Panca", true);
					domanda.aggiungiOpzione("Squat", false);
					domanda.aggiungiOpzione("Stacco", false);
				}
				case ABBINAMENTO -> {
					domanda.aggiungiCoppia("Bicipite", "Braccio");
					domanda.aggiungiCoppia("Quadricipite", "Coscia");
				}
			}
			domanda.getOpzioni().forEach(o -> ReflectionTestUtils.setField(o, "id", UUID.randomUUID()));
			domanda.getElementi().forEach(e -> ReflectionTestUtils.setField(e, "id", UUID.randomUUID()));
			return domanda;
		}

		private static ModificaDomandaRequest opzioni(TestoRequest... testi) {
			return new ModificaDomandaRequest(null, null, List.of(testi), null);
		}

		private static ModificaDomandaRequest elementi(TestoRequest... testi) {
			return new ModificaDomandaRequest(null, null, null, List.of(testi));
		}

		private static UUID opzione(Domanda domanda, int indice) {
			return domanda.getOpzioni().get(indice).getId();
		}

		private static UUID elemento(Domanda domanda, Lato lato, int coppia) {
			return domanda.getElementi().stream()
					.filter(e -> e.getLato() == lato && e.getOrdine() == coppia)
					.findFirst().orElseThrow().getId();
		}

		@Test
		void soloTestoESpiegazioneSempreValidi() {
			ModificaDomandaRequest richiesta = new ModificaDomandaRequest("Nuovo", "Nuova", null, null);

			for (TipoDomanda tipo : TipoDomanda.values()) {
				assertThat(RegoleDomanda.modifica(domanda(tipo), richiesta)).isEmpty();
			}
		}

		@Test
		void testiDiVeroEFalsoNonSiModificano() {
			Domanda domanda = domanda(TipoDomanda.VERO_FALSO);

			assertThat(RegoleDomanda.modifica(domanda, opzioni(new TestoRequest(opzione(domanda, 0), "Giusto"))))
					.containsExactly(entry("opzioni", RegoleDomanda.MSG_VERO_FALSO_FISSI));
		}

		@Test
		void listeDiUnAltroTipo() {
			Domanda abbinamento = domanda(TipoDomanda.ABBINAMENTO);
			Domanda multipla = domanda(TipoDomanda.SCELTA_MULTIPLA);

			assertThat(RegoleDomanda.modifica(abbinamento, opzioni(new TestoRequest(UUID.randomUUID(), "X"))))
					.containsExactly(entry("opzioni", RegoleDomanda.MSG_NON_PREVISTO));
			assertThat(RegoleDomanda.modifica(multipla, elementi(new TestoRequest(opzione(multipla, 0), "X"))))
					.containsExactly(entry("elementi", RegoleDomanda.MSG_NON_PREVISTO));
		}

		@Test
		void idDiUnAltraDomanda() {
			Domanda domanda = domanda(TipoDomanda.SCELTA_MULTIPLA);
			Domanda altra = domanda(TipoDomanda.SCELTA_MULTIPLA);

			assertThat(RegoleDomanda.modifica(domanda, opzioni(new TestoRequest(opzione(domanda, 0), "Ok"),
					new TestoRequest(opzione(altra, 1), "Estranea"))))
					.containsExactly(entry("opzioni[1].id", RegoleDomanda.MSG_ID_ESTRANEO));
		}

		@Test
		void idRipetutoNellaRichiesta() {
			Domanda domanda = domanda(TipoDomanda.SCELTA_MULTIPLA);
			UUID id = opzione(domanda, 1);

			assertThat(RegoleDomanda.modifica(domanda, opzioni(new TestoRequest(id, "X"), new TestoRequest(id, "Y"))))
					.containsExactly(entry("opzioni[1].id", RegoleDomanda.MSG_ID_RIPETUTO));
		}

		@Test
		void opzioneResaUgualeAUnAltraNonModificata() {
			Domanda domanda = domanda(TipoDomanda.SCELTA_MULTIPLA);

			// "Panca" è la prima e non si tocca: l'errore va sull'opzione modificata (la terza, indice 0 nella richiesta)
			assertThat(RegoleDomanda.modifica(domanda, opzioni(new TestoRequest(opzione(domanda, 2), " panca "))))
					.containsExactly(entry("opzioni[0].testo", RegoleDomanda.MSG_OPZIONE_UGUALE));
		}

		@Test
		void scambioDiTestiAmmesso() {
			Domanda domanda = domanda(TipoDomanda.SCELTA_MULTIPLA);

			assertThat(RegoleDomanda.modifica(domanda, opzioni(new TestoRequest(opzione(domanda, 0), "Squat"),
					new TestoRequest(opzione(domanda, 1), "Panca")))).isEmpty();
		}

		@Test
		void elementoResoUgualeNellaStessaColonna() {
			Domanda domanda = domanda(TipoDomanda.ABBINAMENTO);

			assertThat(RegoleDomanda.modifica(domanda,
					elementi(new TestoRequest(elemento(domanda, Lato.SINISTRA, 2), "BICIPITE"))))
					.containsExactly(entry("elementi[0].testo", RegoleDomanda.MSG_SINISTRA_UGUALE));
			assertThat(RegoleDomanda.modifica(domanda,
					elementi(new TestoRequest(elemento(domanda, Lato.DESTRA, 1), "coscia"))))
					.containsExactly(entry("elementi[0].testo", RegoleDomanda.MSG_DESTRA_UGUALE));
		}

		@Test
		void stessoTestoInColonneDiverseAmmesso() {
			Domanda domanda = domanda(TipoDomanda.ABBINAMENTO);

			assertThat(RegoleDomanda.modifica(domanda,
					elementi(new TestoRequest(elemento(domanda, Lato.DESTRA, 1), "Bicipite")))).isEmpty();
		}

	}

	private static java.util.Map.Entry<String, String> entry(String chiave, String valore) {
		return java.util.Map.entry(chiave, valore);
	}

}
