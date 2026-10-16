package it.quackfit.contenuti;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tutte le query vedono solo le tappe attive (@SQLRestriction). Per cancellare non si usa delete():
 * si usa {@link #cancella}, chiamato dal service insieme alla cascata (vedi backend/CLAUDE.md, Soft delete).
 */
public interface TappaRepository extends JpaRepository<Tappa, UUID> {

	List<Tappa> findAllByOrderByOrdineAsc();

	/** Ordine più alto tra le tappe attive (0 se non ce ne sono): una nuova tappa va a max + 1. */
	@Query("select coalesce(max(t.ordine), 0) from Tappa t")
	int ordineMassimo();

	/** Soft delete con l'istante deciso dal service. Una tappa già cancellata non si tocca. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Tappa t set t.deletedAt = :adesso where t.id = :id and t.deletedAt is null")
	int cancella(@Param("id") UUID id, @Param("adesso") Instant adesso);

}
