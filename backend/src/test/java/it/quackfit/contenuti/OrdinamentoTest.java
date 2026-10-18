package it.quackfit.contenuti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.CodiceErrore;

/** Unit test delle regole dell'ordine (admin.md, Ordine): 1…n senza buchi dopo ogni spostamento o cancellazione. */
class OrdinamentoTest {

	private List<Tappa> tappe;

	@BeforeEach
	void setUp() {
		tappe = new ArrayList<>();
		for (String nome : new String[] { "A", "B", "C", "D", "E" }) {
			tappe.add(new Tappa(nome, "Descrizione", tappe.size() + 1, Instant.EPOCH));
		}
	}

	private Tappa tappa(String nome) {
		return tappe.stream().filter(t -> t.getTitolo().equals(nome)).findFirst().orElseThrow();
	}

	/** I titoli nell'ordine assegnato. */
	private String ordine() {
		return String.join("", tappe.stream().sorted(Comparator.comparingInt(Tappa::getOrdine))
				.map(Tappa::getTitolo).toList());
	}

	private void ordiniSenzaBuchi(List<Tappa> elementi) {
		assertThat(elementi.stream().map(Tappa::getOrdine).sorted().toList())
				.isEqualTo(java.util.stream.IntStream.rangeClosed(1, elementi.size()).boxed().toList());
	}

	@ParameterizedTest(name = "{0} alla posizione {1} → {2}")
	@CsvSource({
			"D, 2, ADBCE", // verso l'alto: B e C scendono di uno
			"B, 4, ACDBE", // verso il basso: C e D salgono di uno
			"E, 1, EABCD", // dall'ultima alla prima
			"A, 5, BCDEA", // dalla prima all'ultima
			"C, 3, ABCDE" // stessa posizione: niente cambia
	})
	void spostaEFaScalareGliAltri(String nome, int posizione, String atteso) {
		Ordinamento.sposta(tappe, tappa(nome), posizione, Tappa::setOrdine);

		assertThat(ordine()).isEqualTo(atteso);
		ordiniSenzaBuchi(tappe);
	}

	@ParameterizedTest(name = "posizione {0} su 5 → 400")
	@ValueSource(ints = { 0, -1, 6 })
	void posizioneFuoriLimite(int posizione) {
		assertThatThrownBy(() -> Ordinamento.sposta(tappe, tappa("B"), posizione, Tappa::setOrdine))
				.isInstanceOfSatisfying(ApplicazioneException.class, e -> {
					assertThat(e.getCodice()).isEqualTo(CodiceErrore.VALIDAZIONE);
					assertThat(e.getCampi()).containsEntry("ordine", "La posizione deve essere tra 1 e 5");
				});
		assertThat(ordine()).isEqualTo("ABCDE");
	}

	@Test
	void rinumeraChiudeIlBucoDopoUnaCancellazione() {
		List<Tappa> rimaste = new ArrayList<>(tappe);
		rimaste.remove(tappa("C"));

		Ordinamento.rinumera(rimaste, Tappa::setOrdine);

		assertThat(rimaste).extracting(Tappa::getTitolo, Tappa::getOrdine)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("A", 1), org.assertj.core.groups.Tuple.tuple("B", 2),
						org.assertj.core.groups.Tuple.tuple("D", 3), org.assertj.core.groups.Tuple.tuple("E", 4));
	}

}
