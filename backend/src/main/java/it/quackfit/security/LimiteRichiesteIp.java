package it.quackfit.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Contatori delle richieste per IP, a finestra fissa: per ogni IP l'istante della prima richiesta
 * e quante ne ha fatte da allora. Solo in memoria, mai nel database (l'IP è un dato personale).
 * Vedi backend/CLAUDE.md, sezione Limite per indirizzo IP.
 */
@Component
public class LimiteRichiesteIp {

	static final int MASSIMO_RICHIESTE = 20;
	static final Duration FINESTRA = Duration.ofMinutes(1);

	private record Finestra(Instant inizio, int richieste) {
	}

	private final Map<String, Finestra> finestre = new ConcurrentHashMap<>();
	private final Clock clock;

	public LimiteRichiesteIp(Clock clock) {
		this.clock = clock;
	}

	/**
	 * Registra una richiesta dell'IP e dice se è ammessa.
	 * compute è atomico per chiave: due richieste contemporanee dallo stesso IP non si perdono.
	 */
	public boolean consenti(String ip) {
		Instant adesso = Instant.now(clock);
		Finestra finestra = finestre.compute(ip, (chiave, attuale) -> {
			if (attuale == null || scaduta(attuale, adesso)) {
				return new Finestra(adesso, 1);
			}
			// oltre il limite il contatore resta fermo: basta sapere che è stato superato
			return new Finestra(attuale.inizio(), Math.min(attuale.richieste() + 1, MASSIMO_RICHIESTE + 1));
		});
		return finestra.richieste() <= MASSIMO_RICHIESTE;
	}

	/** Toglie gli IP con la finestra scaduta, così la mappa non cresce all'infinito. */
	@Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
	public void pulisci() {
		Instant adesso = Instant.now(clock);
		// removeIf sui valori di una ConcurrentHashMap toglie la voce solo se nel frattempo non è cambiata
		finestre.values().removeIf(finestra -> scaduta(finestra, adesso));
	}

	/** Quanti IP sono in memoria: serve ai test della pulizia. */
	int ipInMemoria() {
		return finestre.size();
	}

	private static boolean scaduta(Finestra finestra, Instant adesso) {
		return !adesso.isBefore(finestra.inizio().plus(FINESTRA));
	}

}
