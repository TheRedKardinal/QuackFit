package it.quackfit.config;

import java.util.concurrent.Executor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * Attiva i metodi @Async. Oggi li usa solo la password dimenticata (vedi auth.md): tutto il lavoro sul codice
 * gira in background, così la risposta ha la stessa durata che l'email sia registrata o no.
 *
 * L'executor non è un bean (resta dentro questa classe): così l'executor di default di Spring Boot,
 * usato da altre parti del framework, non viene sostituito.
 * Nel profilo test si usa un SyncTaskExecutor: il lavoro "asincrono" finisce prima della risposta
 * e i test possono controllarne subito il risultato.
 *
 * Chiusura ordinata: l'executor lo chiude questa classe, che è uno SmartLifecycle. All'arresto Spring chiama stop()
 * nella fase {@link #FASE}, dopo che il web server ha smesso di accettare richieste e prima di distruggere i bean
 * (database, mail): i lavori già in coda vengono finiti, per al massimo {@link #ATTESA_CHIUSURA_SECONDI} secondi.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer, SmartLifecycle {

	/** Pochi thread: l'unico lavoro è generare e inviare un codice. */
	static final int THREAD = 2;
	/** Oltre questa coda le richieste si scartano: meglio perdere un'email che accumulare lavoro all'infinito. */
	static final int CODA = 100;
	/**
	 * Attesa massima dei lavori in coda all'arresto. Breve, perché Render concede 30 secondi tra SIGTERM e la chiusura
	 * forzata e anche il web server ne usa una parte; basta per finire gli invii in corso (timeout email: 10 secondi).
	 */
	static final int ATTESA_CHIUSURA_SECONDI = 20;
	/** Più bassa di quella del web server: le fasi si fermano dalla più alta, quindi questa si ferma dopo. */
	static final int FASE = WebServerApplicationContext.START_STOP_LIFECYCLE_PHASE - 1024;

	private final ThreadPoolTaskExecutor pool;
	private final Executor executor;
	private volatile boolean attivo;

	public AsyncConfig(Environment environment) {
		if (environment.matchesProfiles("test")) {
			pool = null;
			executor = new SyncTaskExecutor();
		} else {
			pool = new ThreadPoolTaskExecutor();
			pool.setCorePoolSize(THREAD);
			pool.setMaxPoolSize(THREAD);
			pool.setQueueCapacity(CODA);
			pool.setThreadNamePrefix("quackfit-async-");
			// coda piena: si scarta e si logga, senza far fallire la risposta (che è già partita uguale per tutti)
			pool.setRejectedExecutionHandler((lavoro, esecutore) ->
					log.warn("Coda dei lavori asincroni piena: richiesta scartata"));
			// all'arresto shutdown() non accetta nuovi lavori e aspetta quelli già in coda (vedi stop())
			pool.setWaitForTasksToCompleteOnShutdown(true);
			pool.setAwaitTerminationSeconds(ATTESA_CHIUSURA_SECONDI);
			pool.initialize();
			executor = pool;
		}
	}

	@Override
	public Executor getAsyncExecutor() {
		return executor;
	}

	/**
	 * Un errore in un metodo @Async non arriva al client: finisce qui, nel log.
	 * Si scrivono solo la classe dell'errore e quella della causa originale, mai i messaggi né i parametri:
	 * potrebbero contenere l'email o il codice.
	 */
	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return (errore, metodo, parametri) -> log.error("Errore nel lavoro asincrono {}.{}: {} (causa: {})",
				metodo.getDeclaringClass().getSimpleName(), metodo.getName(), errore.getClass().getName(),
				NestedExceptionUtils.getMostSpecificCause(errore).getClass().getName());
	}

	@Override
	public void start() {
		attivo = true;
	}

	/** Non accetta nuovi lavori e aspetta quelli in coda, per al massimo ATTESA_CHIUSURA_SECONDI. */
	@Override
	public void stop() {
		if (pool != null) {
			pool.shutdown();
		}
		attivo = false;
	}

	@Override
	public boolean isRunning() {
		return attivo;
	}

	@Override
	public int getPhase() {
		return FASE;
	}

}
