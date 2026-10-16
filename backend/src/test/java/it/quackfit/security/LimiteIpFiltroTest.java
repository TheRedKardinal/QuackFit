package it.quackfit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.Filter;

/**
 * Test di integrazione del limite per IP nella catena di Spring Security.
 * Ogni test usa un IP inventato e diverso: i contatori sono nel bean condiviso
 * dal contesto Spring, quindi non si sommano a quelli degli altri test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LimiteIpFiltroTest.ControllerProva.class)
class LimiteIpFiltroTest {

	/** Endpoint pubblico finto che conta quante richieste gli arrivano davvero. */
	@RestController
	static class ControllerProva {

		static final AtomicInteger CHIAMATE = new AtomicInteger();

		@PostMapping("/api/auth/prova-limite")
		String prova() {
			CHIAMATE.incrementAndGet();
			return "ok";
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FilterChainProxy filterChainProxy;

	@BeforeEach
	void azzera() {
		ControllerProva.CHIAMATE.set(0);
	}

	private static RequestPostProcessor daIp(String ip) {
		return richiesta -> {
			richiesta.setRemoteAddr(ip);
			return richiesta;
		};
	}

	@Test
	void laVentunesimaRichiestaRiceve429ENonArrivaAlController() throws Exception {
		for (int i = 0; i < LimiteRichiesteIp.MASSIMO_RICHIESTE; i++) {
			mockMvc.perform(post("/api/auth/prova-limite").with(daIp("198.51.100.1")))
					.andExpect(status().isOk());
		}

		mockMvc.perform(post("/api/auth/prova-limite").with(daIp("198.51.100.1")))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(jsonPath("$.codice").value("TROPPE_RICHIESTE"))
				.andExpect(jsonPath("$.errore").value("Too Many Requests"))
				.andExpect(jsonPath("$.messaggio").value("Troppe richieste: riprova tra un minuto"))
				.andExpect(jsonPath("$.timestamp").isNotEmpty());

		assertThat(ControllerProva.CHIAMATE.get()).isEqualTo(LimiteRichiesteIp.MASSIMO_RICHIESTE);
	}

	@Test
	void unAltroIpNonEBloccato() throws Exception {
		for (int i = 0; i <= LimiteRichiesteIp.MASSIMO_RICHIESTE; i++) {
			mockMvc.perform(post("/api/auth/prova-limite").with(daIp("198.51.100.2")));
		}

		mockMvc.perform(post("/api/auth/prova-limite").with(daIp("198.51.100.3")))
				.andExpect(status().isOk());
	}

	@Test
	void leRotteFuoriDaAuthNonSonoLimitate() throws Exception {
		for (int i = 0; i < LimiteRichiesteIp.MASSIMO_RICHIESTE + 5; i++) {
			// senza token: 401, mai 429
			mockMvc.perform(get("/api/qualunque").with(daIp("198.51.100.4")))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	void ilLimiteVienePrimaDelFiltroJwt() {
		List<Filter> filtri = filterChainProxy.getFilters("/api/auth/login");
		int limite = indice(filtri, LimiteIpFiltro.class);
		int jwt = indice(filtri, JwtFiltro.class);

		assertThat(limite).isNotNegative();
		assertThat(limite).isLessThan(jwt);
	}

	private static int indice(List<Filter> filtri, Class<?> tipo) {
		for (int i = 0; i < filtri.size(); i++) {
			if (tipo.isInstance(filtri.get(i))) {
				return i;
			}
		}
		return -1;
	}

}
