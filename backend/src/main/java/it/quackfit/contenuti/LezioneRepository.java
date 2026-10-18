package it.quackfit.contenuti;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tutte le query vedono solo le lezioni attive (@SQLRestriction). Per cancellare non si usa delete():
 * si usano i metodi cancella, chiamati dal service (vedi backend/CLAUDE.md, Soft delete).
 */
public interface LezioneRepository extends JpaRepository<Lezione, UUID> {

	List<Lezione> findByTappaOrderByOrdineAsc(Tappa tappa);

	/** Tutte le lezioni attive, per l'elenco admin (il service le raggruppa per tappa). */
	List<Lezione> findAllByOrderByOrdineAsc();

	/**
	 * Id della tappa di una lezione attiva, senza caricare la lezione: serve per bloccare la tappa prima di leggere
	 * le lezioni da rinumerare, così si leggono già con i valori aggiornati dalle transazioni precedenti.
	 */
	@Query("select l.tappa.id from Lezione l where l.id = :id")
	Optional<UUID> tappaIdDi(@Param("id") UUID id);

	/** Ordine più alto tra le lezioni attive della tappa (0 se non ce ne sono). */
	@Query("select coalesce(max(l.ordine), 0) from Lezione l where l.tappa = :tappa")
	int ordineMassimo(@Param("tappa") Tappa tappa);

	/** Soft delete di una lezione con l'istante deciso dal service. Una lezione già cancellata non si tocca. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Lezione l set l.deletedAt = :adesso where l.id = :id and l.deletedAt is null")
	int cancella(@Param("id") UUID id, @Param("adesso") Instant adesso);

	/** Cascata dalla tappa: cancella le sue lezioni ancora attive, senza toccare quelle già cancellate. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Lezione l set l.deletedAt = :adesso where l.tappa.id = :tappaId and l.deletedAt is null")
	int cancellaDellaTappa(@Param("tappaId") UUID tappaId, @Param("adesso") Instant adesso);

}
