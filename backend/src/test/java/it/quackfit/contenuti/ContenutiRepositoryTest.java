package it.quackfit.contenuti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository dei contenuti con H2: soft delete, cascata con l'istante scelto dal service, ordine, opzioni e abbinamenti.
 * Le righe cancellate si leggono con SQL diretto (JdbcTemplate), che non passa da @SQLRestriction.
 * @DataJpaTest annulla tutto a fine test: niente pulizia da fare.
 */
@DataJpaTest
@ActiveProfiles("test")
class ContenutiRepositoryTest {

	private static final Instant CREAZIONE = Instant.parse("2026-10-01T10:00:00Z");
	private static final Instant PRIMA = Instant.parse("2026-10-10T08:00:00Z");
	private static final Instant ADESSO = Instant.parse("2026-10-16T10:00:00Z");

	@Autowired
	private TappaRepository tappaRepository;

	@Autowired
	private LezioneRepository lezioneRepository;

	@Autowired
	private DomandaRepository domandaRepository;

	@Autowired
	private OpzioneRepository opzioneRepository;

	@Autowired
	private ElementoAbbinamentoRepository elementoRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	private Tappa tappa(int ordine) {
		return tappaRepository.saveAndFlush(new Tappa("Tappa " + ordine, "Descrizione", ordine, CREAZIONE));
	}

	private Lezione lezione(Tappa tappa, int ordine) {
		return lezioneRepository.saveAndFlush(new Lezione(tappa, "Lezione " + ordine, "Descrizione", ordine, CREAZIONE));
	}

	private Domanda domanda(Lezione lezione, int ordine) {
		Domanda domanda = new Domanda(lezione, "Domanda " + ordine, "Spiegazione", TipoDomanda.VERO_FALSO, ordine, CREAZIONE);
		domanda.aggiungiOpzione("Vero", true);
		domanda.aggiungiOpzione("Falso", false);
		return domandaRepository.saveAndFlush(domanda);
	}

	/** Righe nel database, cancellate comprese. */
	private int righe(String tabella) {
		return jdbc.queryForObject("select count(*) from " + tabella, Integer.class);
	}

	/** deleted_at letto direttamente dal database (null = attivo). */
	private Instant deletedAt(String tabella, UUID id) {
		OffsetDateTime valore = jdbc.queryForObject("select deleted_at from " + tabella + " where id = ?",
				OffsetDateTime.class, id);
		return valore == null ? null : valore.toInstant();
	}

	// --- salvataggio e ordine ---

	@Test
	void salvaIContenutiConIValoriIniziali() {
		Lezione lezione = lezione(tappa(1), 1);
		Domanda domanda = domanda(lezione, 1);
		entityManager.clear();

		Domanda letta = domandaRepository.findById(domanda.getId()).orElseThrow();
		assertThat(letta.getTesto()).isEqualTo("Domanda 1");
		assertThat(letta.getTipo()).isEqualTo(TipoDomanda.VERO_FALSO);
		assertThat(letta.getCreatedAt()).isEqualTo(CREAZIONE);
		assertThat(letta.getDeletedAt()).isNull();
		assertThat(letta.getLezione().getId()).isEqualTo(lezione.getId());
		assertThat(letta.getLezione().getTappa().getTitolo()).isEqualTo("Tappa 1");
	}

	@Test
	void listeInOrdine() {
		Tappa seconda = tappa(2);
		Tappa prima = tappa(1);
		Lezione l2 = lezione(prima, 2);
		Lezione l1 = lezione(prima, 1);
		Domanda d2 = domanda(l1, 2);
		Domanda d1 = domanda(l1, 1);

		assertThat(tappaRepository.findAllByOrderByOrdineAsc()).extracting(Tappa::getId)
				.containsExactly(prima.getId(), seconda.getId());
		assertThat(lezioneRepository.findByTappaOrderByOrdineAsc(prima)).extracting(Lezione::getId)
				.containsExactly(l1.getId(), l2.getId());
		assertThat(domandaRepository.findByLezioneOrderByOrdineAsc(l1)).extracting(Domanda::getId)
				.containsExactly(d1.getId(), d2.getId());
	}

	@Test
	void nessunVincoloUniqueSullOrdine() {
		tappa(1);
		tappa(1);

		assertThat(tappaRepository.findAll()).hasSize(2);
	}

	@Test
	void ordineMassimoContaSoloGliAttivi() {
		assertThat(tappaRepository.ordineMassimo()).isZero();
		Tappa t1 = tappa(1);
		Tappa t2 = tappa(2);
		Lezione l1 = lezione(t1, 1);
		Lezione l2 = lezione(t1, 2);
		domanda(l1, 1);
		Domanda d2 = domanda(l1, 2);

		tappaRepository.cancella(t2.getId(), ADESSO);
		lezioneRepository.cancella(l2.getId(), ADESSO);
		domandaRepository.cancella(d2.getId(), ADESSO);

		assertThat(tappaRepository.ordineMassimo()).isEqualTo(1);
		assertThat(lezioneRepository.ordineMassimo(t1)).isEqualTo(1);
		assertThat(domandaRepository.ordineMassimo(l1)).isEqualTo(1);
		assertThat(lezioneRepository.ordineMassimo(t2)).isZero();
	}

	// --- soft delete: un elemento cancellato non compare più nelle query normali ---

	@Test
	void tappaCancellataSparisceMaRestaNelDatabase() {
		Tappa attiva = tappa(1);
		Tappa cancellata = tappa(2);

		tappaRepository.cancella(cancellata.getId(), ADESSO);

		assertThat(tappaRepository.findById(cancellata.getId())).isEmpty();
		assertThat(tappaRepository.findAllByOrderByOrdineAsc()).extracting(Tappa::getId).containsExactly(attiva.getId());
		assertThat(tappaRepository.count()).isEqualTo(1);
		assertThat(righe("tappa")).isEqualTo(2);
	}

	@Test
	void lezioneCancellataSparisceMaRestaNelDatabase() {
		Tappa tappa = tappa(1);
		Lezione attiva = lezione(tappa, 1);
		Lezione cancellata = lezione(tappa, 2);

		lezioneRepository.cancella(cancellata.getId(), ADESSO);

		assertThat(lezioneRepository.findById(cancellata.getId())).isEmpty();
		assertThat(lezioneRepository.findByTappaOrderByOrdineAsc(tappa)).extracting(Lezione::getId)
				.containsExactly(attiva.getId());
		assertThat(righe("lezione")).isEqualTo(2);
	}

	@Test
	void domandaCancellataSparisceMaOpzioniEdElementiRestano() {
		Lezione lezione = lezione(tappa(1), 1);
		Domanda attiva = domanda(lezione, 1);
		Domanda cancellata = domanda(lezione, 2);
		List<UUID> opzioni = cancellata.getOpzioni().stream().map(Opzione::getId).toList();

		domandaRepository.cancella(cancellata.getId(), ADESSO);

		assertThat(domandaRepository.findById(cancellata.getId())).isEmpty();
		assertThat(domandaRepository.findByLezioneOrderByOrdineAsc(lezione)).extracting(Domanda::getId)
				.containsExactly(attiva.getId());
		assertThat(domandaRepository.countByLezione(lezione)).isEqualTo(1);
		assertThat(righe("domanda")).isEqualTo(2);
		// le opzioni restano: il resoconto dei tentativi deve poterle mostrare
		assertThat(opzioneRepository.findAllById(opzioni)).hasSize(2);
	}

	/**
	 * delete() del repository non si usa, ma se qualcuno lo chiama @SQLDelete fa un soft delete senza cascata.
	 * Il contesto va svuotato prima: con la lezione ancora caricata, Hibernate rifiuterebbe di "rimuovere" una tappa
	 * a cui lei punta (un motivo in più per usare solo i metodi cancella dei service).
	 */
	@Test
	void deleteDelRepositoryESoloUnaReteDiSicurezza() {
		Tappa tappa = tappa(1);
		Lezione lezione = lezione(tappa, 1);
		entityManager.clear();

		tappaRepository.delete(tappaRepository.findById(tappa.getId()).orElseThrow());
		tappaRepository.flush();

		assertThat(righe("tappa")).isEqualTo(1);
		assertThat(deletedAt("tappa", tappa.getId())).isNotNull();
		assertThat(tappaRepository.findById(tappa.getId())).isEmpty();
		// nessuna cascata: la lezione resta attiva
		assertThat(deletedAt("lezione", lezione.getId())).isNull();
	}

	// --- cancellazione con l'istante del service e cascata ---

	@Test
	void cancellaScriveEsattamenteLIstanteDelService() {
		Tappa tappa = tappa(1);

		assertThat(tappaRepository.cancella(tappa.getId(), ADESSO)).isEqualTo(1);

		assertThat(deletedAt("tappa", tappa.getId())).isEqualTo(ADESSO);
	}

	/** La sequenza del service: domande, poi lezioni, poi la tappa, tutte con lo stesso istante. */
	@Test
	void cascataDallaTappaConUnSoloIstante() {
		Tappa tappa = tappa(1);
		Tappa altra = tappa(2);
		Lezione l1 = lezione(tappa, 1);
		Lezione l2 = lezione(tappa, 2);
		Domanda d1 = domanda(l1, 1);
		Domanda d2 = domanda(l2, 1);
		Domanda diAltra = domanda(lezione(altra, 1), 1);

		domandaRepository.cancellaDellaTappa(tappa.getId(), ADESSO);
		lezioneRepository.cancellaDellaTappa(tappa.getId(), ADESSO);
		tappaRepository.cancella(tappa.getId(), ADESSO);

		assertThat(deletedAt("tappa", tappa.getId())).isEqualTo(ADESSO);
		assertThat(deletedAt("lezione", l1.getId())).isEqualTo(ADESSO);
		assertThat(deletedAt("lezione", l2.getId())).isEqualTo(ADESSO);
		assertThat(deletedAt("domanda", d1.getId())).isEqualTo(ADESSO);
		assertThat(deletedAt("domanda", d2.getId())).isEqualTo(ADESSO);
		// l'altra tappa non si tocca
		assertThat(deletedAt("tappa", altra.getId())).isNull();
		assertThat(deletedAt("domanda", diAltra.getId())).isNull();
	}

	/** La cascata non sovrascrive il deleted_at di ciò che era già cancellato. */
	@Test
	void laCascataNonToccaCioCheEraGiaCancellato() {
		Tappa tappa = tappa(1);
		Lezione attiva = lezione(tappa, 1);
		Lezione giaCancellata = lezione(tappa, 2);
		Domanda dellaCancellata = domanda(giaCancellata, 1);
		Domanda giaCancellataNellaAttiva = domanda(attiva, 1);
		Domanda ancoraAttiva = domanda(attiva, 2);
		// cancellazioni precedenti
		domandaRepository.cancellaDellaLezione(giaCancellata.getId(), PRIMA);
		lezioneRepository.cancella(giaCancellata.getId(), PRIMA);
		domandaRepository.cancella(giaCancellataNellaAttiva.getId(), PRIMA);

		domandaRepository.cancellaDellaTappa(tappa.getId(), ADESSO);
		lezioneRepository.cancellaDellaTappa(tappa.getId(), ADESSO);
		tappaRepository.cancella(tappa.getId(), ADESSO);

		assertThat(deletedAt("lezione", giaCancellata.getId())).isEqualTo(PRIMA);
		assertThat(deletedAt("domanda", dellaCancellata.getId())).isEqualTo(PRIMA);
		assertThat(deletedAt("domanda", giaCancellataNellaAttiva.getId())).isEqualTo(PRIMA);
		assertThat(deletedAt("lezione", attiva.getId())).isEqualTo(ADESSO);
		assertThat(deletedAt("domanda", ancoraAttiva.getId())).isEqualTo(ADESSO);
		// e cancellare di nuovo la tappa non cambia nulla
		assertThat(tappaRepository.cancella(tappa.getId(), ADESSO.plusSeconds(60))).isZero();
		assertThat(deletedAt("tappa", tappa.getId())).isEqualTo(ADESSO);
	}

	@Test
	void cascataDallaLezione() {
		Tappa tappa = tappa(1);
		Lezione lezione = lezione(tappa, 1);
		Domanda giaCancellata = domanda(lezione, 1);
		Domanda attiva = domanda(lezione, 2);
		domandaRepository.cancella(giaCancellata.getId(), PRIMA);

		domandaRepository.cancellaDellaLezione(lezione.getId(), ADESSO);
		lezioneRepository.cancella(lezione.getId(), ADESSO);

		assertThat(deletedAt("lezione", lezione.getId())).isEqualTo(ADESSO);
		assertThat(deletedAt("domanda", attiva.getId())).isEqualTo(ADESSO);
		assertThat(deletedAt("domanda", giaCancellata.getId())).isEqualTo(PRIMA);
		assertThat(deletedAt("tappa", tappa.getId())).isNull();
	}

	// --- opzioni e abbinamenti ---

	@Test
	void opzioniSalvateConLaDomandaEInOrdine() {
		Lezione lezione = lezione(tappa(1), 1);
		Domanda domanda = new Domanda(lezione, "Quale?", "Perché", TipoDomanda.SCELTA_MULTIPLA, 1, CREAZIONE);
		domanda.aggiungiOpzione("A", false);
		domanda.aggiungiOpzione("B", true);
		domanda.aggiungiOpzione("C", false);
		domandaRepository.saveAndFlush(domanda);
		entityManager.clear();

		Domanda letta = domandaRepository.findById(domanda.getId()).orElseThrow();
		assertThat(opzioneRepository.findByDomandaOrderByOrdineAsc(letta))
				.extracting(Opzione::getTesto, Opzione::isCorretta, Opzione::getOrdine)
				.containsExactly(
						tuple("A", false, 1),
						tuple("B", true, 2),
						tuple("C", false, 3));
		assertThat(letta.getOpzioni()).extracting(Opzione::getTesto).containsExactly("A", "B", "C");
	}

	@Test
	void coppieDiAbbinamentoSalvateConLaDomanda() {
		Lezione lezione = lezione(tappa(1), 1);
		Domanda domanda = new Domanda(lezione, "Abbina", "Perché", TipoDomanda.ABBINAMENTO, 1, CREAZIONE);
		domanda.aggiungiCoppia("Bicipite", "Braccio");
		domanda.aggiungiCoppia("Quadricipite", "Coscia");
		domandaRepository.saveAndFlush(domanda);
		entityManager.clear();

		List<ElementoAbbinamento> elementi = elementoRepository
				.findByDomandaOrderByLatoAscOrdineAsc(domandaRepository.findById(domanda.getId()).orElseThrow());
		assertThat(elementi).extracting(ElementoAbbinamento::getLato, ElementoAbbinamento::getTesto)
				.containsExactly(
						tuple(Lato.DESTRA, "Braccio"),
						tuple(Lato.DESTRA, "Coscia"),
						tuple(Lato.SINISTRA, "Bicipite"),
						tuple(Lato.SINISTRA, "Quadricipite"));
		// i DESTRA non puntano a nulla, i SINISTRA al DESTRA della loro coppia
		assertThat(elementi.subList(0, 2)).allSatisfy(destro -> assertThat(destro.getAbbinamentoCorretto()).isNull());
		assertThat(elementi.get(2).getAbbinamentoCorretto().getTesto()).isEqualTo("Braccio");
		assertThat(elementi.get(3).getAbbinamentoCorretto().getTesto()).isEqualTo("Coscia");
		assertThat(elementi.get(2).getOrdine()).isEqualTo(elementi.get(0).getOrdine()).isEqualTo(1);
	}

	@Test
	void unDestraPuoEssereLAbbinamentoDiUnSoloSinistra() {
		Lezione lezione = lezione(tappa(1), 1);
		Domanda domanda = new Domanda(lezione, "Abbina", "Perché", TipoDomanda.ABBINAMENTO, 1, CREAZIONE);
		domanda.aggiungiCoppia("Bicipite", "Braccio");
		domandaRepository.saveAndFlush(domanda);
		ElementoAbbinamento destro = domanda.getElementi().get(0);

		ElementoAbbinamento secondo = new ElementoAbbinamento(domanda, "Tricipite", Lato.SINISTRA, destro, 2);

		assertThatThrownBy(() -> elementoRepository.saveAndFlush(secondo))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
