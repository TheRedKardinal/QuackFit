package it.quackfit.tentativi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import it.quackfit.common.ClockConfig;
import it.quackfit.contenuti.Domanda;
import it.quackfit.contenuti.DomandaRepository;
import it.quackfit.contenuti.ElementoAbbinamento;
import it.quackfit.contenuti.Lezione;
import it.quackfit.contenuti.LezioneRepository;
import it.quackfit.contenuti.Opzione;
import it.quackfit.contenuti.Tappa;
import it.quackfit.contenuti.TappaRepository;
import it.quackfit.contenuti.TipoDomanda;
import it.quackfit.utente.Utente;
import it.quackfit.utente.UtenteRepository;
import jakarta.persistence.EntityNotFoundException;

/**
 * Repository dei tentativi con H2: valori iniziali, proprietario, lezioni completate, vincoli UNIQUE e query native
 * del resoconto (ResocontoQuery), che devono raggiungere anche i contenuti cancellati dopo il tentativo.
 * @DataJpaTest annulla tutto a fine test: niente pulizia da fare. ResocontoQuery non è un repository Spring Data:
 * @DataJpaTest non lo crea da solo, va importato.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(ResocontoQuery.class)
class TentativiRepositoryTest {

	private static final Instant CREAZIONE = Instant.parse("2026-10-01T10:00:00Z");
	private static final Instant INIZIO = Instant.parse("2026-10-16T10:00:00Z");
	private static final Instant FINE = Instant.parse("2026-10-16T10:05:00Z");
	private static final Instant CANCELLAZIONE = Instant.parse("2026-10-16T12:00:00Z");

	@Autowired
	private TentativoLezioneRepository tentativoRepository;

	@Autowired
	private RispostaOpzioneRepository rispostaOpzioneRepository;

	@Autowired
	private RispostaAbbinamentoRepository rispostaAbbinamentoRepository;

	@Autowired
	private UtenteRepository utenteRepository;

	@Autowired
	private TappaRepository tappaRepository;

	@Autowired
	private LezioneRepository lezioneRepository;

	@Autowired
	private DomandaRepository domandaRepository;

	@Autowired
	private ResocontoQuery resocontoQuery;

	@Autowired
	private TestEntityManager entityManager;

	private Utente mario;
	private Utente luigi;
	private Tappa tappa;
	private Lezione lezione;

	@BeforeEach
	void setUp() {
		mario = utenteRepository.save(new Utente("Mario", "Rossi", "mario@example.com", "hash", CREAZIONE));
		luigi = utenteRepository.save(new Utente("Luigi", "Verdi", "luigi@example.com", "hash", CREAZIONE));
		tappa = tappaRepository.save(new Tappa("Tappa", "Descrizione", 1, CREAZIONE));
		lezione = lezione(1);
	}

	private Lezione lezione(int ordine) {
		return lezioneRepository.saveAndFlush(new Lezione(tappa, "Lezione " + ordine, "Descrizione", ordine, CREAZIONE));
	}

	private TentativoLezione tentativo(Utente utente, Lezione lezione, EsitoTentativo esito) {
		TentativoLezione tentativo = new TentativoLezione(utente, lezione, INIZIO);
		tentativo.setEsito(esito);
		return tentativoRepository.saveAndFlush(tentativo);
	}

	private Domanda sceltaMultipla(int ordine) {
		Domanda domanda = new Domanda(lezione, "Domanda " + ordine, "Spiegazione " + ordine, TipoDomanda.SCELTA_MULTIPLA,
				ordine, CREAZIONE);
		domanda.aggiungiOpzione("A", false);
		domanda.aggiungiOpzione("B", true);
		return domandaRepository.saveAndFlush(domanda);
	}

	/** Elementi in lista: [destro 1, sinistro 1, destro 2, sinistro 2]. */
	private Domanda abbinamento(int ordine) {
		Domanda domanda = new Domanda(lezione, "Abbina", "Spiegazione", TipoDomanda.ABBINAMENTO, ordine, CREAZIONE);
		domanda.aggiungiCoppia("Bicipite", "Braccio");
		domanda.aggiungiCoppia("Quadricipite", "Coscia");
		return domandaRepository.saveAndFlush(domanda);
	}

	// --- tentativo ---

	@Test
	void nuovoTentativoConIValoriIniziali() {
		TentativoLezione salvato = tentativoRepository.saveAndFlush(new TentativoLezione(mario, lezione, INIZIO));
		entityManager.clear();

		TentativoLezione letto = tentativoRepository.findById(salvato.getId()).orElseThrow();
		assertThat(letto.getEsito()).isEqualTo(EsitoTentativo.IN_CORSO);
		assertThat(letto.getErrori()).isZero();
		assertThat(letto.getIniziatoAt()).isEqualTo(INIZIO);
		assertThat(letto.getFinitoAt()).isNull();
	}

	@Test
	void unTentativoAltruiRisultaInesistente() {
		TentativoLezione diMario = tentativo(mario, lezione, EsitoTentativo.IN_CORSO);

		assertThat(tentativoRepository.findByIdAndUtenteId(diMario.getId(), mario.getId())).isPresent();
		assertThat(tentativoRepository.findByIdAndUtenteId(diMario.getId(), luigi.getId())).isEmpty();
	}

	@Test
	void tentativiInCorsoDiUtenteELezione() {
		TentativoLezione inCorso = tentativo(mario, lezione, EsitoTentativo.IN_CORSO);
		tentativo(mario, lezione, EsitoTentativo.FALLITO);
		tentativo(luigi, lezione, EsitoTentativo.IN_CORSO);
		tentativo(mario, lezione(2), EsitoTentativo.IN_CORSO);

		assertThat(tentativoRepository.findByUtenteIdAndLezioneIdAndEsito(mario.getId(), lezione.getId(),
				EsitoTentativo.IN_CORSO)).extracting(TentativoLezione::getId).containsExactly(inCorso.getId());
	}

	// --- lezioni completate ---

	@Test
	void lezioniCompletateSoloSuperateAttiveEDellUtente() {
		Lezione superata = lezione;
		Lezione fallita = lezione(2);
		Lezione inCorso = lezione(3);
		Lezione superataPoiCancellata = lezione(4);
		Lezione superataDaLuigi = lezione(5);
		tentativo(mario, superata, EsitoTentativo.SUPERATO);
		tentativo(mario, superata, EsitoTentativo.SUPERATO); // rifatta: compare una volta sola
		tentativo(mario, fallita, EsitoTentativo.FALLITO);
		tentativo(mario, inCorso, EsitoTentativo.IN_CORSO);
		tentativo(mario, superataPoiCancellata, EsitoTentativo.SUPERATO);
		tentativo(luigi, superataDaLuigi, EsitoTentativo.SUPERATO);
		lezioneRepository.cancella(superataPoiCancellata.getId(), CANCELLAZIONE);

		assertThat(tentativoRepository.lezioniCompletate(mario.getId())).containsExactly(superata.getId());
	}

	// --- vincoli UNIQUE: ultima difesa contro due prime risposte in parallelo ---

	@Test
	void unaSolaRispostaOpzionePerDomandaNelTentativo() {
		TentativoLezione tentativo = tentativo(mario, lezione, EsitoTentativo.IN_CORSO);
		Domanda domanda = sceltaMultipla(1);
		rispostaOpzioneRepository.saveAndFlush(new RispostaOpzione(tentativo, domanda, domanda.getOpzioni().get(0), false));

		RispostaOpzione seconda = new RispostaOpzione(tentativo, domanda, domanda.getOpzioni().get(1), true);

		assertThatThrownBy(() -> rispostaOpzioneRepository.saveAndFlush(seconda))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void ogniSinistroUnaVoltaSolaNelTentativo() {
		TentativoLezione tentativo = tentativo(mario, lezione, EsitoTentativo.IN_CORSO);
		Domanda domanda = abbinamento(1);
		List<ElementoAbbinamento> e = domanda.getElementi();
		rispostaAbbinamentoRepository.saveAndFlush(new RispostaAbbinamento(tentativo, domanda, e.get(1), e.get(0), true));

		RispostaAbbinamento stessoSinistro = new RispostaAbbinamento(tentativo, domanda, e.get(1), e.get(2), false);

		assertThatThrownBy(() -> rispostaAbbinamentoRepository.saveAndFlush(stessoSinistro))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void ogniDestroUnaVoltaSolaNelTentativo() {
		TentativoLezione tentativo = tentativo(mario, lezione, EsitoTentativo.IN_CORSO);
		Domanda domanda = abbinamento(1);
		List<ElementoAbbinamento> e = domanda.getElementi();
		rispostaAbbinamentoRepository.saveAndFlush(new RispostaAbbinamento(tentativo, domanda, e.get(1), e.get(0), true));

		RispostaAbbinamento stessoDestro = new RispostaAbbinamento(tentativo, domanda, e.get(3), e.get(0), false);

		assertThatThrownBy(() -> rispostaAbbinamentoRepository.saveAndFlush(stessoDestro))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rispostePerTentativoEDomanda() {
		TentativoLezione tentativo = tentativo(mario, lezione, EsitoTentativo.IN_CORSO);
		Domanda multipla = sceltaMultipla(1);
		Domanda abbina = abbinamento(2);
		List<ElementoAbbinamento> e = abbina.getElementi();
		rispostaOpzioneRepository.saveAndFlush(new RispostaOpzione(tentativo, multipla, multipla.getOpzioni().get(1), true));
		rispostaAbbinamentoRepository.saveAndFlush(new RispostaAbbinamento(tentativo, abbina, e.get(1), e.get(0), true));
		rispostaAbbinamentoRepository.saveAndFlush(new RispostaAbbinamento(tentativo, abbina, e.get(3), e.get(2), true));

		assertThat(rispostaOpzioneRepository.findByTentativoIdAndDomandaId(tentativo.getId(), multipla.getId()))
				.isPresent();
		assertThat(rispostaOpzioneRepository.findByTentativoIdAndDomandaId(tentativo.getId(), abbina.getId()))
				.isEmpty();
		assertThat(rispostaAbbinamentoRepository.findByTentativoIdAndDomandaId(tentativo.getId(), abbina.getId()))
				.hasSize(2);
	}

	// --- resoconto: query native che raggiungono anche i contenuti cancellati ---

	@Test
	void ilResocontoMostraLezioneDomandeEOpzioniCancellateDopoIlTentativo() {
		TentativoLezione tentativo = tentativo(mario, lezione, EsitoTentativo.SUPERATO);
		tentativo.setErrori(1);
		tentativo.setFinitoAt(FINE);
		tentativoRepository.saveAndFlush(tentativo);
		Domanda seconda = sceltaMultipla(2);
		Domanda prima = sceltaMultipla(1);
		Domanda abbina = abbinamento(3);
		List<Opzione> opzioniPrima = prima.getOpzioni();
		List<ElementoAbbinamento> e = abbina.getElementi();
		rispostaOpzioneRepository.save(new RispostaOpzione(tentativo, seconda, seconda.getOpzioni().get(1), true));
		rispostaOpzioneRepository.save(new RispostaOpzione(tentativo, prima, opzioniPrima.get(0), true));
		// abbinamento con le coppie scambiate: Bicipite → Coscia, Quadricipite → Braccio
		rispostaAbbinamentoRepository.save(new RispostaAbbinamento(tentativo, abbina, e.get(1), e.get(2), true));
		rispostaAbbinamentoRepository.save(new RispostaAbbinamento(tentativo, abbina, e.get(3), e.get(0), true));
		rispostaAbbinamentoRepository.flush();
		// dopo il tentativo l'admin cancella tutto: domande e lezione
		domandaRepository.cancellaDellaLezione(lezione.getId(), CANCELLAZIONE);
		lezioneRepository.cancella(lezione.getId(), CANCELLAZIONE);

		IntestazioneResoconto intestazione = resocontoQuery.intestazione(tentativo.getId(), mario.getId()).orElseThrow();
		assertThat(intestazione).isEqualTo(new IntestazioneResoconto(tentativo.getId(), EsitoTentativo.SUPERATO, 1,
				INIZIO, FINE, lezione.getId(), "Lezione 1"));

		// domande nell'ordine dell'admin, con prima risposta e soluzione
		assertThat(resocontoQuery.righeOpzioni(tentativo.getId(), mario.getId()))
				.extracting(RigaResocontoOpzione::domandaId, RigaResocontoOpzione::testo, RigaResocontoOpzione::tipo,
						RigaResocontoOpzione::spiegazione, RigaResocontoOpzione::opzioneId,
						RigaResocontoOpzione::opzioneTesto, RigaResocontoOpzione::corretta,
						RigaResocontoOpzione::soluzioneId, RigaResocontoOpzione::soluzioneTesto)
				.containsExactly(
						tuple(prima.getId(), "Domanda 1", TipoDomanda.SCELTA_MULTIPLA, "Spiegazione 1",
								opzioniPrima.get(0).getId(), "A", false, opzioniPrima.get(1).getId(), "B"),
						tuple(seconda.getId(), "Domanda 2", TipoDomanda.SCELTA_MULTIPLA, "Spiegazione 2",
								seconda.getOpzioni().get(1).getId(), "B", true, seconda.getOpzioni().get(1).getId(), "B"));

		assertThat(resocontoQuery.righeAbbinamenti(tentativo.getId(), mario.getId()))
				.extracting(RigaResocontoAbbinamento::domandaId, RigaResocontoAbbinamento::tipo,
						RigaResocontoAbbinamento::sinistroId, RigaResocontoAbbinamento::sinistroTesto,
						RigaResocontoAbbinamento::destroId, RigaResocontoAbbinamento::destroTesto,
						RigaResocontoAbbinamento::soluzioneId, RigaResocontoAbbinamento::soluzioneTesto)
				.containsExactly(
						tuple(abbina.getId(), TipoDomanda.ABBINAMENTO, e.get(1).getId(), "Bicipite",
								e.get(2).getId(), "Coscia", e.get(0).getId(), "Braccio"),
						tuple(abbina.getId(), TipoDomanda.ABBINAMENTO, e.get(3).getId(), "Quadricipite",
								e.get(0).getId(), "Braccio", e.get(2).getId(), "Coscia"));
	}

	@Test
	void ilResocontoDiUnTentativoAltruiEVuoto() {
		TentativoLezione tentativo = tentativo(mario, lezione, EsitoTentativo.IN_CORSO);
		Domanda domanda = sceltaMultipla(1);
		Domanda abbina = abbinamento(2);
		rispostaOpzioneRepository.saveAndFlush(new RispostaOpzione(tentativo, domanda, domanda.getOpzioni().get(1), true));
		rispostaAbbinamentoRepository.saveAndFlush(new RispostaAbbinamento(tentativo, abbina,
				abbina.getElementi().get(1), abbina.getElementi().get(0), true));

		assertThat(resocontoQuery.intestazione(tentativo.getId(), luigi.getId())).isEmpty();
		assertThat(resocontoQuery.righeOpzioni(tentativo.getId(), luigi.getId())).isEmpty();
		assertThat(resocontoQuery.righeAbbinamenti(tentativo.getId(), luigi.getId())).isEmpty();
	}

	@Test
	void ilResocontoDiUnTentativoInCorsoSenzaRisposte() {
		TentativoLezione tentativo = tentativo(mario, lezione, EsitoTentativo.IN_CORSO);

		IntestazioneResoconto intestazione = resocontoQuery.intestazione(tentativo.getId(), mario.getId()).orElseThrow();
		assertThat(intestazione.esito()).isEqualTo(EsitoTentativo.IN_CORSO);
		assertThat(intestazione.finitoAt()).isNull();
		assertThat(resocontoQuery.righeOpzioni(tentativo.getId(), mario.getId())).isEmpty();
	}

	/**
	 * L'istante letto dal RowMapper (OffsetDateTime → Instant) è lo stesso salvato, al microsecondo: nessuno
	 * spostamento di fuso. Clock fisso fuori dall'ora intera e con i microsecondi, per accorgersi di arrotondamenti.
	 */
	@Test
	void ilResocontoLeggeGliIstantiEsattiSalvatiConIlClock() {
		Clock clock = Clock.fixed(Instant.parse("2026-03-29T01:30:15.123456Z"), ClockConfig.FUSO);
		TentativoLezione tentativo = new TentativoLezione(mario, lezione, Instant.now(clock));
		tentativo.setFinitoAt(Instant.now(clock).plusSeconds(90));
		tentativoRepository.saveAndFlush(tentativo);

		IntestazioneResoconto intestazione = resocontoQuery.intestazione(tentativo.getId(), mario.getId()).orElseThrow();

		assertThat(intestazione.iniziatoAt()).isEqualTo(Instant.parse("2026-03-29T01:30:15.123456Z"));
		assertThat(intestazione.finitoAt()).isEqualTo(Instant.parse("2026-03-29T01:31:45.123456Z"));
	}

	/**
	 * DOCUMENTAZIONE, non protezione: mostra perché dalle risposte non si naviga verso la domanda.
	 * @SQLRestriction vale anche per le relazioni, quindi una domanda cancellata non si raggiunge con getDomanda().
	 * Se un giorno Hibernate cambiasse comportamento questo test fallirebbe, ma le query native resterebbero giuste.
	 */
	@Test
	void documentazioneDallaRispostaNonSiRaggiungeLaDomandaCancellata() {
		TentativoLezione tentativo = tentativo(mario, lezione, EsitoTentativo.IN_CORSO);
		Domanda domanda = sceltaMultipla(1);
		RispostaOpzione risposta = rispostaOpzioneRepository
				.saveAndFlush(new RispostaOpzione(tentativo, domanda, domanda.getOpzioni().get(1), true));
		domandaRepository.cancella(domanda.getId(), CANCELLAZIONE);
		entityManager.clear();

		RispostaOpzione letta = rispostaOpzioneRepository.findById(risposta.getId()).orElseThrow();

		assertThatThrownBy(() -> letta.getDomanda().getTesto()).isInstanceOf(EntityNotFoundException.class);
	}

}
