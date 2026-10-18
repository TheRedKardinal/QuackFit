package it.quackfit.contenuti;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * Tutte le query vedono solo le tappe attive (@SQLRestriction). Per cancellare non si usa delete():
 * si usa {@link #cancella}, chiamato dal service insieme alla cascata (vedi backend/CLAUDE.md, Soft delete).
 */
public interface TappaRepository extends JpaRepository<Tappa, UUID> {

	List<Tappa> findAllByOrderByOrdineAsc();

	/**
	 * Tutte le tappe attive con un lock pessimistico (SELECT ... FOR UPDATE) fino alla fine della transazione. Lo
	 * chiamano per prima cosa le operazioni che cambiano l'ordine delle tappe (creazione, riordino, cancellazione):
	 * due admin si mettono in fila e gli ordini restano 1…n. Sempre per ordine e id, così le righe si bloccano
	 * ovunque nella stessa sequenza e due transazioni non si aspettano a vicenda (deadlock).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from Tappa t order by t.ordine, t.id")
	List<Tappa> bloccaTutte();

	/**
	 * Una tappa attiva con un lock pessimistico. Lo chiamano le operazioni che cambiano l'ordine delle sue lezioni:
	 * si blocca la tappa genitore, che esiste anche quando la tappa non ha ancora lezioni.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from Tappa t where t.id = :id")
	Optional<Tappa> blocca(@Param("id") UUID id);

	/** Ordine più alto tra le tappe attive (0 se non ce ne sono): una nuova tappa va a max + 1. */
	@Query("select coalesce(max(t.ordine), 0) from Tappa t")
	int ordineMassimo();

	/** Soft delete con l'istante deciso dal service. Una tappa già cancellata non si tocca. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Tappa t set t.deletedAt = :adesso where t.id = :id and t.deletedAt is null")
	int cancella(@Param("id") UUID id, @Param("adesso") Instant adesso);

}
