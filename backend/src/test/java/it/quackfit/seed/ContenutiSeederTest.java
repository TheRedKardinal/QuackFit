package it.quackfit.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import it.quackfit.contenuti.AdminContenutiService;
import it.quackfit.contenuti.AdminDomandeService;
import it.quackfit.contenuti.CreazioneLezioneRequest;
import it.quackfit.contenuti.CreazioneTappaRequest;
import it.quackfit.contenuti.DomandaAdminResponse;
import it.quackfit.contenuti.LezioneAdminResponse;
import it.quackfit.contenuti.TappaAdminResponse;
import it.quackfit.contenuti.TipoDomanda;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Test del seeder dei contenuti con H2. Nel profilo test il seeder non parte da solo: lo si costruisce e lo si esegue
 * a mano, simulando uno o più avvii dell'app (come SuperadminSeederTest).
 */
@SpringBootTest
@ActiveProfiles("test")
class ContenutiSeederTest {

	@Autowired
	private AdminContenutiService adminContenutiService;

	@Autowired
	private AdminDomandeService adminDomandeService;

	@Autowired
	private JdbcTemplate jdbc;

	/** Tappa, lezione e domanda hanno il soft delete: deleteAll() non toglierebbe le righe. */
	@BeforeEach
	@AfterEach
	void pulisci() {
		jdbc.update("update elemento_abbinamento set abbinamento_corretto_id = null");
		jdbc.update("delete from elemento_abbinamento");
		jdbc.update("delete from opzione");
		jdbc.update("delete from domanda");
		jdbc.update("delete from lezione");
		jdbc.update("delete from tappa");
	}

	/** Un "avvio" dell'app. */
	private void avvio() {
		new ContenutiSeeder(jdbc, adminContenutiService, adminDomandeService).run();
	}

	@Test
	void primoAvvioInserisceTappeLezioniEDomandeInOrdine() {
		avvio();

		List<TappaAdminResponse> tappe = adminContenutiService.elenco();
		assertThat(tappe).extracting(TappaAdminResponse::titolo)
				.containsExactlyElementsOf(ContenutiIniziali.TAPPE.stream().map(ContenutiIniziali.TappaIniziale::titolo).toList());
		assertThat(tappe).extracting(TappaAdminResponse::ordine).containsExactly(1, 2, 3);
		for (TappaAdminResponse tappa : tappe) {
			assertThat(tappa.lezioni()).extracting(LezioneAdminResponse::ordine).containsExactly(1, 2, 3);
			for (LezioneAdminResponse lezione : tappa.lezioni()) {
				List<DomandaAdminResponse> domande = adminContenutiService.dettaglioLezione(lezione.id()).domande();
				assertThat(domande).extracting(DomandaAdminResponse::ordine).containsExactly(1, 2, 3, 4, 5);
				// ogni lezione ha tutti e tre i tipi
				assertThat(domande).extracting(DomandaAdminResponse::tipo)
						.containsAll(EnumSet.allOf(TipoDomanda.class));
				// vero/falso e scelta multipla: una sola opzione giusta; abbinamento: da 2 a 6 coppie
				for (DomandaAdminResponse domanda : domande) {
					if (domanda.tipo() == TipoDomanda.ABBINAMENTO) {
						assertThat(domanda.coppie()).hasSizeBetween(2, 6);
					} else {
						assertThat(domanda.opzioni()).filteredOn(DomandaAdminResponse.OpzioneAdminResponse::corretta)
								.hasSize(1);
					}
				}
			}
		}
		assertThat(jdbc.queryForObject("select count(*) from domanda", Integer.class)).isEqualTo(45);
	}

	@Test
	void secondoAvvioNonDuplica() {
		avvio();
		avvio();

		assertThat(jdbc.queryForObject("select count(*) from tappa", Integer.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from lezione", Integer.class)).isEqualTo(9);
		assertThat(jdbc.queryForObject("select count(*) from domanda", Integer.class)).isEqualTo(45);
	}

	@Test
	void conUnaTappaGiaPresenteNonFaNulla() {
		// contenuti creati dall'editor: il seeder non li tocca e non ne aggiunge
		var tappa = adminContenutiService.creaTappa(new CreazioneTappaRequest("Mia tappa", "Creata dall'admin"));
		adminContenutiService.creaLezione(tappa.id(), new CreazioneLezioneRequest("Mia lezione", "Descrizione"));

		avvio();

		assertThat(adminContenutiService.elenco()).singleElement()
				.satisfies(sola -> assertThat(sola.titolo()).isEqualTo("Mia tappa"));
		assertThat(jdbc.queryForObject("select count(*) from domanda", Integer.class)).isZero();
	}

	@Test
	void conLeTappeTutteCancellateNonFaNulla() {
		// gli admin hanno cancellato tutto (soft delete): le righe restano e il percorso di partenza non torna
		var tappa = adminContenutiService.creaTappa(new CreazioneTappaRequest("Vecchia tappa", "Poi cancellata"));
		adminContenutiService.cancellaTappa(tappa.id());
		assertThat(adminContenutiService.elenco()).isEmpty();

		avvio();

		assertThat(adminContenutiService.elenco()).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from tappa", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from domanda", Integer.class)).isZero();
	}

	@Test
	void tuttiITestiRispettanoLeRegoleDeiRecord() {
		// le stesse annotazioni che l'editor controlla con @Valid (lunghezze, obbligatori)
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
		for (var tappa : ContenutiIniziali.TAPPE) {
			assertThat(validator.validate(new CreazioneTappaRequest(tappa.titolo(), tappa.descrizione()))).isEmpty();
			for (var lezione : tappa.lezioni()) {
				assertThat(validator.validate(new CreazioneLezioneRequest(lezione.titolo(), lezione.descrizione())))
						.isEmpty();
				IntStream.range(0, lezione.domande().size()).forEach(i -> assertThat(validator.validate(lezione.domande().get(i)))
						.as("%s, domanda %d", lezione.titolo(), i + 1)
						.isEmpty());
			}
		}
	}

}
