package it.quackfit.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.quackfit.common.ClockConfig;

/** Unit test del limite per IP, con un orologio fermo che si sposta a mano. */
class LimiteRichiesteIpTest {

	private static final String IP = "203.0.113.1";
	private static final String ALTRO_IP = "203.0.113.2";

	/** Clock fisso che il test può far avanzare. */
	private static class OrologioRegolabile extends Clock {

		private Instant adesso = Instant.parse("2026-10-16T10:00:00Z");

		void avanza(Duration durata) {
			adesso = adesso.plus(durata);
		}

		@Override
		public Instant instant() {
			return adesso;
		}

		@Override
		public ZoneId getZone() {
			return ClockConfig.FUSO;
		}

		@Override
		public Clock withZone(ZoneId zona) {
			return this;
		}

	}

	private OrologioRegolabile orologio;
	private LimiteRichiesteIp limite;

	@BeforeEach
	void setUp() {
		orologio = new OrologioRegolabile();
		limite = new LimiteRichiesteIp(orologio);
	}

	private void consumaTutteLeRichieste(String ip) {
		for (int i = 1; i <= LimiteRichiesteIp.MASSIMO_RICHIESTE; i++) {
			assertThat(limite.consenti(ip)).as("richiesta %d", i).isTrue();
		}
	}

	@Test
	void ventiRichiesteAmmesseLaVentunesimaNo() {
		consumaTutteLeRichieste(IP);

		assertThat(limite.consenti(IP)).isFalse();
	}

	@Test
	void restaBloccatoFinoAllaFineDellaFinestra() {
		consumaTutteLeRichieste(IP);

		orologio.avanza(Duration.ofSeconds(59));

		assertThat(limite.consenti(IP)).isFalse();
	}

	@Test
	void finestraScadutaSiRiparte() {
		consumaTutteLeRichieste(IP);
		assertThat(limite.consenti(IP)).isFalse();

		orologio.avanza(Duration.ofSeconds(60));

		// nuova finestra: di nuovo 20 richieste
		consumaTutteLeRichieste(IP);
		assertThat(limite.consenti(IP)).isFalse();
	}

	@Test
	void ipDiversiHannoContatoriIndipendenti() {
		consumaTutteLeRichieste(IP);

		assertThat(limite.consenti(IP)).isFalse();
		assertThat(limite.consenti(ALTRO_IP)).isTrue();
	}

	@Test
	void laFinestraParteDallaPrimaRichiesta() {
		// 10 richieste subito, 10 dopo 30 secondi: sono tutte nella stessa finestra
		for (int i = 0; i < 10; i++) {
			limite.consenti(IP);
		}
		orologio.avanza(Duration.ofSeconds(30));
		for (int i = 0; i < 10; i++) {
			assertThat(limite.consenti(IP)).isTrue();
		}

		assertThat(limite.consenti(IP)).isFalse();
	}

	@Test
	void laPuliziaToglieSoloGliIpConLaFinestraScaduta() {
		limite.consenti(IP);
		orologio.avanza(Duration.ofSeconds(40));
		limite.consenti(ALTRO_IP);
		orologio.avanza(Duration.ofSeconds(30));
		// IP: finestra iniziata 70 secondi fa (scaduta); ALTRO_IP: 30 secondi fa (ancora valida)

		limite.pulisci();

		assertThat(limite.ipInMemoria()).isEqualTo(1);
		// ALTRO_IP conserva il suo conteggio: 1 richiesta già fatta, ne restano 19
		for (int i = 0; i < LimiteRichiesteIp.MASSIMO_RICHIESTE - 1; i++) {
			assertThat(limite.consenti(ALTRO_IP)).isTrue();
		}
		assertThat(limite.consenti(ALTRO_IP)).isFalse();
	}

}
