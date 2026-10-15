package it.quackfit.common;

import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Test dell'ExceptionsHandler con MockMvc standalone: un controller finto lancia gli errori,
 * senza avviare tutta l'applicazione (niente Spring Security, niente database).
 */
class ExceptionsHandlerTest {

	private static final Instant ADESSO = Instant.parse("2026-10-14T12:00:00Z");

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(ADESSO, ClockConfig.FUSO);
		mockMvc = MockMvcBuilders.standaloneSetup(new ControllerFinto())
				.setControllerAdvice(new ExceptionsHandler(clock))
				.build();
	}

	@Test
	void validazioneRestituisceICampiSbagliati() throws Exception {
		mockMvc.perform(post("/finto/validazione")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\": \"\", \"email\": \"non-una-email\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.errore").value("Bad Request"))
				.andExpect(jsonPath("$.messaggio").value("Uno o più campi non sono validi"))
				.andExpect(jsonPath("$.campi.nome").value("Il nome è obbligatorio"))
				.andExpect(jsonPath("$.campi.email").value("Formato email non valido"))
				.andExpect(jsonPath("$.timestamp").value("2026-10-14T12:00:00Z"));
	}

	@Test
	void eccezioneApplicativaUsaCodiceEStatus() throws Exception {
		mockMvc.perform(get("/finto/applicazione"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.codice").value("EMAIL_GIA_USATA"))
				.andExpect(jsonPath("$.errore").value("Conflict"))
				.andExpect(jsonPath("$.messaggio").value("Email già registrata"))
				.andExpect(jsonPath("$.campi").value(anEmptyMap()))
				.andExpect(jsonPath("$.timestamp").value("2026-10-14T12:00:00Z"));
	}

	@Test
	void erroreImprevistoNonMostraDettagliTecnici() throws Exception {
		mockMvc.perform(get("/finto/imprevisto"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.codice").value("ERRORE_INTERNO"))
				.andExpect(jsonPath("$.messaggio").value("Si è verificato un errore imprevisto, riprova più tardi"))
				.andExpect(jsonPath("$.campi").value(anEmptyMap()))
				.andExpect(content().string(not(containsString("dettaglio interno"))))
				.andExpect(content().string(not(containsString("IllegalStateException"))));
	}

	@Test
	void bodyMalformatoEValidazione() throws Exception {
		mockMvc.perform(post("/finto/validazione")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\": "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi").value(anEmptyMap()));
	}

	@Test
	void idNonUuidEValidazione() throws Exception {
		mockMvc.perform(get("/finto/lezioni/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codice").value("VALIDAZIONE"))
				.andExpect(jsonPath("$.campi.id").value("Valore non valido"));
	}

	@Test
	void endpointInesistenteENonTrovato() throws Exception {
		mockMvc.perform(get("/finto/inesistente"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codice").value("NON_TROVATO"));
	}

	@Test
	void metodoNonPrevistoEMetodoNonConsentito() throws Exception {
		mockMvc.perform(delete("/finto/applicazione"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.status").value(405))
				.andExpect(jsonPath("$.codice").value("METODO_NON_CONSENTITO"));
	}

	record RichiestaFinta(
			@NotBlank(message = "Il nome è obbligatorio") String nome,
			@Email(message = "Formato email non valido") String email) {
	}

	@RestController
	static class ControllerFinto {

		@PostMapping("/finto/validazione")
		void validazione(@Valid @RequestBody RichiestaFinta richiesta) {
		}

		@GetMapping("/finto/applicazione")
		void applicazione() {
			throw new ApplicazioneException(CodiceErrore.EMAIL_GIA_USATA, "Email già registrata");
		}

		@GetMapping("/finto/imprevisto")
		void imprevisto() {
			throw new IllegalStateException("dettaglio interno");
		}

		@GetMapping("/finto/lezioni/{id}")
		void lezione(@PathVariable UUID id) {
		}

	}

}
