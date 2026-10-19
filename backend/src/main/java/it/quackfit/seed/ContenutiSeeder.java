package it.quackfit.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.contenuti.AdminContenutiService;
import it.quackfit.contenuti.AdminDomandeService;
import it.quackfit.contenuti.CreazioneDomandaRequest;
import it.quackfit.contenuti.CreazioneLezioneRequest;
import it.quackfit.contenuti.CreazioneTappaRequest;
import it.quackfit.seed.ContenutiIniziali.LezioneIniziale;
import it.quackfit.seed.ContenutiIniziali.TappaIniziale;
import lombok.extern.slf4j.Slf4j;

/**
 * All'avvio inserisce i contenuti iniziali del percorso (ContenutiIniziali) **solo se la tabella tappa è vuota**,
 * contando anche le tappe cancellate (vedi backend/CLAUDE.md, Seeder): ai riavvii non fa nulla, quindi niente duplicati,
 * nessuna sovrascrittura di ciò che gli admin hanno cambiato e nessun ritorno del percorso se lo hanno cancellato tutto.
 *
 * Passa dai service dell'editor admin, così valgono le stesse regole: ordini 1…n, regole dei tipi di domanda
 * (RegoleDomanda) e lock. Tutto in una transazione: se una domanda non rispettasse le regole, non entra nulla e
 * l'app non parte (meglio di un percorso a metà, che ai riavvii non verrebbe più completato).
 *
 * Non gira nel profilo test, come SuperadminSeeder: il suo test lo chiama a mano.
 */
@Slf4j
@Component
@Profile("!test")
public class ContenutiSeeder implements CommandLineRunner {

	private final JdbcTemplate jdbc;
	private final AdminContenutiService adminContenutiService;
	private final AdminDomandeService adminDomandeService;

	public ContenutiSeeder(JdbcTemplate jdbc, AdminContenutiService adminContenutiService,
			AdminDomandeService adminDomandeService) {
		this.jdbc = jdbc;
		this.adminContenutiService = adminContenutiService;
		this.adminDomandeService = adminDomandeService;
	}

	@Override
	@Transactional
	public void run(String... argomenti) {
		// SQL nativo: le query di Spring Data non vedono le tappe cancellate (@SQLRestriction), qui servono anche quelle
		Long righe = jdbc.queryForObject("select count(*) from tappa", Long.class);
		if (righe != null && righe > 0) {
			return;
		}
		int lezioni = 0;
		int domande = 0;
		for (TappaIniziale tappa : ContenutiIniziali.TAPPE) {
			var tappaCreata = adminContenutiService.creaTappa(new CreazioneTappaRequest(tappa.titolo(), tappa.descrizione()));
			for (LezioneIniziale lezione : tappa.lezioni()) {
				var lezioneCreata = adminContenutiService.creaLezione(tappaCreata.id(),
						new CreazioneLezioneRequest(lezione.titolo(), lezione.descrizione()));
				lezioni++;
				for (CreazioneDomandaRequest domanda : lezione.domande()) {
					adminDomandeService.crea(lezioneCreata.id(), domanda);
					domande++;
				}
			}
		}
		log.info("Contenuti iniziali inseriti: {} tappe, {} lezioni, {} domande", ContenutiIniziali.TAPPE.size(),
				lezioni, domande);
	}

}
