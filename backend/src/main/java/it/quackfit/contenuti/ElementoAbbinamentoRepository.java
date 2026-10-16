package it.quackfit.contenuti;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ElementoAbbinamentoRepository extends JpaRepository<ElementoAbbinamento, UUID> {

	/** Elementi di una domanda: prima i DESTRA e poi i SINISTRA, ognuno in ordine di coppia. */
	List<ElementoAbbinamento> findByDomandaOrderByLatoAscOrdineAsc(Domanda domanda);

}
