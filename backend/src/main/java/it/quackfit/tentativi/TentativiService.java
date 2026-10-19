package it.quackfit.tentativi;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.quackfit.common.ApplicazioneException;
import it.quackfit.common.ClockConfig;
import it.quackfit.common.CodiceErrore;
import it.quackfit.contenuti.Domanda;
import it.quackfit.contenuti.DomandaRepository;
import it.quackfit.contenuti.ElementoAbbinamento;
import it.quackfit.contenuti.Lato;
import it.quackfit.contenuti.Lezione;
import it.quackfit.contenuti.LezioneRepository;
import it.quackfit.contenuti.Opzione;
import it.quackfit.contenuti.PercorsoService;
import it.quackfit.contenuti.TipoDomanda;
import it.quackfit.tentativi.EsitoRispostaResponse.CoppiaSoluzioneResponse;
import it.quackfit.tentativi.EsitoRispostaResponse.SoluzioneResponse;
import it.quackfit.tentativi.RispostaRequest.CoppiaRequest;
import it.quackfit.utente.Streak;
import it.quackfit.utente.StreakRepository;
import it.quackfit.utente.UtenteRepository;

/**
 * Avvio di un tentativo, risposte e chiusura (tentativi.md, Regole generali, Avvio, Risposta, Chiusura).
 *
 * Dal tentativo e dalle risposte non si naviga verso i contenuti (possono essere cancellati): si usano query con gli id
 * (backend/CLAUDE.md, Soft delete). Le risposte leggono il tentativo con un lock pessimistico, così due risposte quasi
 * insieme vanno in fila e gli errori e la prima risposta salvata restano coerenti; le UNIQUE delle risposte restano
 * come rete di sicurezza. La chiusura blocca il tentativo e poi la streak (sempre in quest'ordine).
 */
@Service
public class TentativiService {

	/** Al terzo errore il tentativo è FALLITO; gli errori non superano mai questo valore. */
	public static final int MAX_ERRORI = 3;

	static final String MSG_TENTATIVO_NON_TROVATO = "Tentativo non trovato";
	static final String MSG_TENTATIVO_CHIUSO = "Questo tentativo è già concluso";
	static final String MSG_LEZIONE_NON_TROVATA = "Lezione non trovata";
	static final String MSG_DOMANDA_NON_TROVATA = "Domanda non trovata";
	static final String MSG_DOMANDA_ALTRA_LEZIONE = "Domanda non di questa lezione";
	static final String MSG_DOMANDA_AGGIUNTA = "Domanda aggiunta dopo l'inizio del tentativo: rifai la lezione per vederla";
	static final String MSG_OPZIONE_OBBLIGATORIA = "L'opzione è obbligatoria per questa domanda";
	static final String MSG_OPZIONE_NON_PREVISTA = "L'opzione non è prevista per questa domanda";
	static final String MSG_COPPIE_OBBLIGATORIE = "Le coppie sono obbligatorie per questa domanda";
	static final String MSG_COPPIE_NON_PREVISTE = "Le coppie non sono previste per questa domanda";
	static final String MSG_OPZIONE_ALTRA_DOMANDA = "Opzione non di questa domanda";
	static final String MSG_COPPIE_NON_VALIDE =
			"Abbina ogni elemento di sinistra a un elemento di destra diverso, una sola volta";
	static final String MSG_GIA_RISOLTA = "Domanda già risolta";
	static final String MSG_NON_TUTTE_RISOLTE =
			"Rispondi correttamente a tutte le domande prima di concludere la lezione";

	private final TentativoLezioneRepository tentativoRepository;
	private final RispostaOpzioneRepository rispostaOpzioneRepository;
	private final RispostaAbbinamentoRepository rispostaAbbinamentoRepository;
	private final LezioneRepository lezioneRepository;
	private final DomandaRepository domandaRepository;
	private final UtenteRepository utenteRepository;
	private final StreakRepository streakRepository;
	private final PercorsoService percorsoService;
	private final NamedParameterJdbcTemplate jdbc;
	private final Clock clock;

	public TentativiService(TentativoLezioneRepository tentativoRepository,
			RispostaOpzioneRepository rispostaOpzioneRepository,
			RispostaAbbinamentoRepository rispostaAbbinamentoRepository, LezioneRepository lezioneRepository,
			DomandaRepository domandaRepository, UtenteRepository utenteRepository, StreakRepository streakRepository,
			PercorsoService percorsoService, NamedParameterJdbcTemplate jdbc, Clock clock) {
		this.tentativoRepository = tentativoRepository;
		this.rispostaOpzioneRepository = rispostaOpzioneRepository;
		this.rispostaAbbinamentoRepository = rispostaAbbinamentoRepository;
		this.lezioneRepository = lezioneRepository;
		this.domandaRepository = domandaRepository;
		this.utenteRepository = utenteRepository;
		this.streakRepository = streakRepository;
		this.percorsoService = percorsoService;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// --- avvio ---

	/**
	 * Avvio (tentativi.md, Avvio): gli stessi controlli del contenuto della lezione (PercorsoService), poi gli
	 * eventuali tentativi IN_CORSO dello stesso utente sulla stessa lezione diventano FALLITO e se ne crea uno nuovo.
	 */
	@Transactional
	public AvvioTentativoResponse avvia(UUID utenteId, UUID lezioneId) {
		Lezione lezione = percorsoService.lezioneAvviabile(utenteId, lezioneId);
		Instant adesso = Instant.now(clock);
		for (TentativoLezione inCorso : tentativoRepository.findByUtenteIdAndLezioneIdAndEsito(utenteId, lezioneId,
				EsitoTentativo.IN_CORSO)) {
			inCorso.setEsito(EsitoTentativo.FALLITO);
			inCorso.setFinitoAt(adesso);
		}
		TentativoLezione tentativo = new TentativoLezione(utenteRepository.getReferenceById(utenteId), lezione, adesso);
		return new AvvioTentativoResponse(tentativoRepository.save(tentativo).getId());
	}

	// --- risposta ---

	/** La domanda letta anche se cancellata (SQL nativo): serve a distinguere "cancellata" da "di un'altra lezione". */
	private record DomandaGrezza(UUID lezioneId, Instant createdAt, boolean cancellata) {
	}

	/**
	 * Una risposta (tentativi.md, Risposta). Controlli in ordine:
	 * 1. tentativo inesistente o di un altro utente → 404 (stesso messaggio)
	 * 2. tentativo concluso → 409 TENTATIVO_CHIUSO
	 * 3. lezione cancellata, o nessuna domanda del tentativo ancora attiva → 404
	 * 4. domanda: inesistente o di un'altra lezione → 400; cancellata → 404; aggiunta dopo l'avvio → 400
	 * 5. campo giusto per il tipo, opzione della domanda, coppie complete → 400 con il campo
	 * 6. domanda già risolta (già data una risposta corretta) → 400
	 * Poi valutazione, errori (massimo MAX_ERRORI, al terzo FALLITO) e salvataggio della sola prima risposta.
	 */
	@Transactional
	public EsitoRispostaResponse rispondi(UUID utenteId, UUID tentativoId, RispostaRequest richiesta) {
		TentativoLezione tentativo = bloccaInCorso(utenteId, tentativoId);
		UUID lezioneId = lezioneAttivaDel(tentativo);

		Domanda domanda = domandaDelTentativo(richiesta.domandaId(), lezioneId, tentativo.getIniziatoAt());
		boolean opzioni = domanda.getTipo() != TipoDomanda.ABBINAMENTO;
		controllaCampi(opzioni, richiesta);

		return opzioni ? rispondiConOpzione(tentativo, domanda, richiesta.opzioneId())
				: rispondiConCoppie(tentativo, domanda, richiesta.coppie());
	}

	/**
	 * Controlli comuni a risposta e chiusura, in ordine: tentativo inesistente o di un altro utente → 404 (stesso
	 * messaggio); tentativo concluso → 409 TENTATIVO_CHIUSO. Il tentativo resta bloccato fino alla fine della transazione.
	 */
	private TentativoLezione bloccaInCorso(UUID utenteId, UUID tentativoId) {
		TentativoLezione tentativo = tentativoRepository.bloccaDellUtente(tentativoId, utenteId)
				.orElseThrow(() -> new ApplicazioneException(CodiceErrore.NON_TROVATO, MSG_TENTATIVO_NON_TROVATO));
		if (tentativo.getEsito() != EsitoTentativo.IN_CORSO) {
			throw new ApplicazioneException(CodiceErrore.TENTATIVO_CHIUSO, MSG_TENTATIVO_CHIUSO);
		}
		return tentativo;
	}

	/** Lezione cancellata, o nessuna domanda del tentativo ancora attiva → 404; altrimenti l'id della lezione. */
	private UUID lezioneAttivaDel(TentativoLezione tentativo) {
		UUID lezioneId = lezioneDel(tentativo.getId());
		if (!lezioneRepository.existsById(lezioneId)
				|| domandaRepository.contaAttiveCreateEntro(lezioneId, tentativo.getIniziatoAt()) == 0) {
			throw new ApplicazioneException(CodiceErrore.NON_TROVATO, MSG_LEZIONE_NON_TROVATA);
		}
		return lezioneId;
	}

	/** Lezione del tentativo con una query sull'id (dal tentativo non si naviga verso la lezione, può essere cancellata). */
	private UUID lezioneDel(UUID tentativoId) {
		return jdbc.queryForObject("select lezione_id from tentativo_lezione where id = :id", Map.of("id", tentativoId),
				(riga, n) -> riga.getObject("lezione_id", UUID.class));
	}

	private Domanda domandaDelTentativo(UUID domandaId, UUID lezioneId, Instant avvio) {
		Optional<DomandaGrezza> grezza = jdbc.query(
				"select lezione_id, created_at, deleted_at from domanda where id = :id", Map.of("id", domandaId),
				(riga, n) -> new DomandaGrezza(riga.getObject("lezione_id", UUID.class),
						riga.getObject("created_at", OffsetDateTime.class).toInstant(),
						riga.getObject("deleted_at", OffsetDateTime.class) != null))
				.stream().findFirst();
		if (grezza.isEmpty() || !grezza.get().lezioneId().equals(lezioneId)) {
			throw campo("domandaId", MSG_DOMANDA_ALTRA_LEZIONE);
		}
		if (grezza.get().cancellata()) {
			throw new ApplicazioneException(CodiceErrore.NON_TROVATO, MSG_DOMANDA_NON_TROVATA);
		}
		if (grezza.get().createdAt().isAfter(avvio)) {
			throw campo("domandaId", MSG_DOMANDA_AGGIUNTA);
		}
		return domandaRepository.findById(domandaId)
				.orElseThrow(() -> new ApplicazioneException(CodiceErrore.NON_TROVATO, MSG_DOMANDA_NON_TROVATA));
	}

	/** Il campo del tipo deve esserci e l'altro no. */
	private static void controllaCampi(boolean opzioni, RispostaRequest richiesta) {
		Map<String, String> campi = new LinkedHashMap<>();
		if (opzioni) {
			if (richiesta.opzioneId() == null) {
				campi.put("opzioneId", MSG_OPZIONE_OBBLIGATORIA);
			}
			if (richiesta.coppie() != null) {
				campi.put("coppie", MSG_COPPIE_NON_PREVISTE);
			}
		} else {
			if (richiesta.coppie() == null) {
				campi.put("coppie", MSG_COPPIE_OBBLIGATORIE);
			}
			if (richiesta.opzioneId() != null) {
				campi.put("opzioneId", MSG_OPZIONE_NON_PREVISTA);
			}
		}
		if (!campi.isEmpty()) {
			throw new ApplicazioneException(CodiceErrore.VALIDAZIONE, "Uno o più campi non sono validi", campi);
		}
	}

	private EsitoRispostaResponse rispondiConOpzione(TentativoLezione tentativo, Domanda domanda, UUID opzioneId) {
		// un'opzione inesistente e una di un'altra domanda danno lo stesso errore
		Opzione scelta = domanda.getOpzioni().stream()
				.filter(opzione -> opzione.getId().equals(opzioneId))
				.findFirst()
				.orElseThrow(() -> campo("opzioneId", MSG_OPZIONE_ALTRA_DOMANDA));
		Optional<RispostaOpzione> prima = rispostaOpzioneRepository.findByTentativoIdAndDomandaId(tentativo.getId(),
				domanda.getId());
		if (prima.isPresent() && prima.get().isRisolta()) {
			throw campo("domandaId", MSG_GIA_RISOLTA);
		}

		boolean corretta = scelta.isCorretta();
		aggiungiErrori(tentativo, corretta ? 0 : 1);
		if (prima.isEmpty()) {
			rispostaOpzioneRepository.save(new RispostaOpzione(tentativo, domanda, scelta, corretta));
		} else if (corretta) {
			prima.get().setRisolta(true);
		}

		UUID giusta = domanda.getOpzioni().stream().filter(Opzione::isCorretta).findFirst().orElseThrow().getId();
		return new EsitoRispostaResponse(corretta, new SoluzioneResponse(giusta, List.of()), domanda.getSpiegazione(),
				tentativo.getErrori(), tentativo.getEsito());
	}

	private EsitoRispostaResponse rispondiConCoppie(TentativoLezione tentativo, Domanda domanda,
			List<CoppiaRequest> coppie) {
		Map<UUID, ElementoAbbinamento> sinistri = new LinkedHashMap<>();
		Map<UUID, ElementoAbbinamento> destri = new LinkedHashMap<>();
		for (ElementoAbbinamento elemento : domanda.getElementi()) {
			(elemento.getLato() == Lato.SINISTRA ? sinistri : destri).put(elemento.getId(), elemento);
		}
		// invio malformato: ogni sinistro una volta sola, destri diversi tra loro, tutti di questa domanda
		Set<UUID> sinistriUsati = new HashSet<>();
		Set<UUID> destriUsati = new HashSet<>();
		boolean valide = coppie.size() == sinistri.size();
		for (CoppiaRequest coppia : coppie) {
			valide &= sinistri.containsKey(coppia.sinistroId()) && sinistriUsati.add(coppia.sinistroId())
					&& destri.containsKey(coppia.destroId()) && destriUsati.add(coppia.destroId());
		}
		if (!valide) {
			throw campo("coppie", MSG_COPPIE_NON_VALIDE);
		}
		List<RispostaAbbinamento> prima = rispostaAbbinamentoRepository.findByTentativoIdAndDomandaId(tentativo.getId(),
				domanda.getId());
		if (!prima.isEmpty() && prima.get(0).isRisolta()) {
			throw campo("domandaId", MSG_GIA_RISOLTA);
		}

		// ogni coppia sbagliata vale un errore
		int sbagliate = (int) coppie.stream()
				.filter(coppia -> !sinistri.get(coppia.sinistroId()).getAbbinamentoCorretto().getId()
						.equals(coppia.destroId()))
				.count();
		boolean corretta = sbagliate == 0;
		aggiungiErrori(tentativo, sbagliate);
		if (prima.isEmpty()) {
			for (CoppiaRequest coppia : coppie) {
				rispostaAbbinamentoRepository.save(new RispostaAbbinamento(tentativo, domanda,
						sinistri.get(coppia.sinistroId()), destri.get(coppia.destroId()), corretta));
			}
		} else if (corretta) {
			prima.forEach(riga -> riga.setRisolta(true));
		}

		List<CoppiaSoluzioneResponse> giuste = sinistri.values().stream()
				.map(sinistro -> new CoppiaSoluzioneResponse(sinistro.getId(), sinistro.getAbbinamentoCorretto().getId()))
				.toList();
		return new EsitoRispostaResponse(corretta, new SoluzioneResponse(null, giuste), domanda.getSpiegazione(),
				tentativo.getErrori(), tentativo.getEsito());
	}

	/** Gli errori non superano MAX_ERRORI; raggiunto il massimo il tentativo è FALLITO. */
	private void aggiungiErrori(TentativoLezione tentativo, int nuovi) {
		if (nuovi == 0) {
			return;
		}
		tentativo.setErrori(Math.min(MAX_ERRORI, tentativo.getErrori() + nuovi));
		if (tentativo.getErrori() == MAX_ERRORI) {
			tentativo.setEsito(EsitoTentativo.FALLITO);
			tentativo.setFinitoAt(Instant.now(clock));
		}
	}

	// --- chiusura ---

	/**
	 * Chiusura (tentativi.md, Chiusura). Controlli in ordine: tentativo inesistente o di un altro utente → 404;
	 * concluso → 409; lezione cancellata o senza domande del tentativo → 404; non tutte le domande del tentativo (attive
	 * e create entro l'avvio) risolte → 400. Poi SUPERATO e streak aggiornata, letta con un lock dopo il tentativo.
	 */
	@Transactional
	public ChiusuraTentativoResponse chiudi(UUID utenteId, UUID tentativoId) {
		TentativoLezione tentativo = bloccaInCorso(utenteId, tentativoId);
		UUID lezioneId = lezioneAttivaDel(tentativo);
		// le query delle risposte passano dalla domanda: quelle cancellate restano fuori da sole
		Set<UUID> risolte = new HashSet<>(rispostaOpzioneRepository.domandeRisolte(tentativoId));
		risolte.addAll(rispostaAbbinamentoRepository.domandeRisolte(tentativoId));
		if (!risolte.containsAll(domandaRepository.idAttiveCreateEntro(lezioneId, tentativo.getIniziatoAt()))) {
			throw new ApplicazioneException(CodiceErrore.VALIDAZIONE, MSG_NON_TUTTE_RISOLTE);
		}

		Instant adesso = Instant.now(clock);
		tentativo.setEsito(EsitoTentativo.SUPERATO);
		tentativo.setFinitoAt(adesso);
		// ogni utente attivo ha la sua Streak (registrazione e seeder): se manca è un dato incoerente → 500
		Streak streak = streakRepository.bloccaDellUtente(utenteId)
				.orElseThrow(() -> new IllegalStateException("Streak mancante per l'utente " + utenteId));
		aggiornaStreak(streak, LocalDate.ofInstant(adesso, ClockConfig.FUSO), adesso);
		return ChiusuraTentativoResponse.da(tentativo, streak);
	}

	/**
	 * Streak dopo una lezione superata oggi (fuso Europe/Rome): ultima attività oggi → invariata; ieri → +1; altrimenti
	 * (mai o più vecchia) → 1. Poi record = max(record, valore) e ultima attività = oggi.
	 */
	static void aggiornaStreak(Streak streak, LocalDate oggi, Instant adesso) {
		LocalDate ultima = streak.getUltimaAttivita();
		if (oggi.equals(ultima)) {
			return;
		}
		streak.setValore(oggi.minusDays(1).equals(ultima) ? streak.getValore() + 1 : 1);
		streak.setRecord(Math.max(streak.getRecord(), streak.getValore()));
		streak.setUltimaAttivita(oggi);
		streak.setUpdatedAt(adesso);
	}

	private static ApplicazioneException campo(String campo, String messaggio) {
		return new ApplicazioneException(CodiceErrore.VALIDAZIONE, "Uno o più campi non sono validi",
				Map.of(campo, messaggio));
	}

}
