package it.quackfit.contenuti;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OpzioneRepository extends JpaRepository<Opzione, UUID> {

	List<Opzione> findByDomandaOrderByOrdineAsc(Domanda domanda);

}
