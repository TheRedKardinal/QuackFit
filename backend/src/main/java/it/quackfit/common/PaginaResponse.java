package it.quackfit.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Una pagina di risultati (liste admin, vedi admin.md): il contenuto e i dati della paginazione,
 * con nomi in italiano invece del formato di Spring Data.
 */
public record PaginaResponse<T>(List<T> contenuto, int pagina, int dimensione, long totaleElementi, int totalePagine) {

	/** Converte una pagina di entità nella pagina di DTO, con la funzione di conversione data (il metodo {@code da}). */
	public static <E, T> PaginaResponse<T> da(Page<E> pagina, Function<E, T> conversione) {
		return new PaginaResponse<>(pagina.getContent().stream().map(conversione).toList(), pagina.getNumber(),
				pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
	}

}
