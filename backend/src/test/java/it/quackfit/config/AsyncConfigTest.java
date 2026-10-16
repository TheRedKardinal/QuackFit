package it.quackfit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Unit test dell'executor di @Async: sincrono nel profilo test, pool piccolo con coda limitata altrimenti. */
@ExtendWith(OutputCaptureExtension.class)
class AsyncConfigTest {

	@Test
	void nelProfiloTestLExecutorESincrono() {
		MockEnvironment ambiente = new MockEnvironment();
		ambiente.setActiveProfiles("test");

		assertThat(new AsyncConfig(ambiente).getAsyncExecutor()).isInstanceOf(SyncTaskExecutor.class);
	}

	@Test
	void fuoriDaiTestPoolPiccoloConCodaLimitata() {
		AsyncConfig config = new AsyncConfig(new MockEnvironment());
		try {
			assertThat(config.getAsyncExecutor()).isInstanceOfSatisfying(ThreadPoolTaskExecutor.class, pool -> {
				assertThat(pool.getCorePoolSize()).isEqualTo(AsyncConfig.THREAD);
				assertThat(pool.getMaxPoolSize()).isEqualTo(AsyncConfig.THREAD);
				assertThat(pool.getQueueCapacity()).isEqualTo(AsyncConfig.CODA);
			});
		} finally {
			config.stop();
		}
	}

	/** All'arresto i lavori già in coda vengono finiti, non persi. */
	@Test
	void allArrestoILavoriInCodaVengonoFiniti() {
		AsyncConfig config = new AsyncConfig(new MockEnvironment());
		config.start();
		AtomicInteger finiti = new AtomicInteger();
		for (int i = 0; i < 4; i++) { // più dei thread: due lavori restano in coda
			config.getAsyncExecutor().execute(() -> {
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
				finiti.incrementAndGet();
			});
		}

		config.stop();

		assertThat(finiti.get()).isEqualTo(4);
		assertThat(config.isRunning()).isFalse();
	}

	@Test
	void siFermaDopoIlWebServer() {
		assertThat(new AsyncConfig(new MockEnvironment()).getPhase())
				.isLessThan(WebServerApplicationContext.START_STOP_LIFECYCLE_PHASE);
	}

	/** L'errore finisce nel log con la sua classe e quella della causa: né i messaggi né i parametri (email, codice). */
	@Test
	void unErroreNelLavoroAsincronoVieneLoggatoSenzaDati(CapturedOutput log) throws Exception {
		MockEnvironment ambiente = new MockEnvironment();
		ambiente.setActiveProfiles("test");
		AsyncConfig config = new AsyncConfig(ambiente);

		config.getAsyncUncaughtExceptionHandler().handleUncaughtException(
				new IllegalStateException("mario@example.com 123456", new ConnectException("mario@example.com")),
				Object.class.getMethod("toString"), "mario@example.com");

		assertThat(log.getOut())
				.contains("Object.toString", "java.lang.IllegalStateException", "java.net.ConnectException")
				.doesNotContain("mario@example.com", "123456");
	}

}
