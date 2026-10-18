package it.quackfit.contenuti;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tutte le query vedono solo le domande attive (@SQLRestriction). Per cancellare non si usa delete():
 * si usano i metodi cancella, chiamati dal service (vedi backend/CLAUDE.md, Soft delete).
 */
public interface DomandaRepository extends JpaRepository<Domanda, UUID> {

	List<Domanda> findByLezioneOrderByOrdineAsc(Lezione lezione);

	long countByLezione(Lezione lezione);

	/** Numero di domande attive per ogni lezione che ne ha (le lezioni senza domande non compaiono). */
	@Query("select new it.quackfit.contenuti.ConteggioDomande(d.lezione.id, count(d)) from Domanda d group by d.lezione.id")
	List<ConteggioDomande> contaPerLezione();

	/** Ordine più alto tra le domande attive della lezione (0 se non ce ne sono). */
	@Query("select coalesce(max(d.ordine), 0) from Domanda d where d.lezione = :lezione")
	int ordineMassimo(@Param("lezione") Lezione lezione);

	/** Soft delete di una domanda con l'istante deciso dal service. Una domanda già cancellata non si tocca. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Domanda d set d.deletedAt = :adesso where d.id = :id and d.deletedAt is null")
	int cancella(@Param("id") UUID id, @Param("adesso") Instant adesso);

	/** Cascata dalla lezione: cancella le sue domande ancora attive, senza toccare quelle già cancellate. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Domanda d set d.deletedAt = :adesso where d.lezione.id = :lezioneId and d.deletedAt is null")
	int cancellaDellaLezione(@Param("lezioneId") UUID lezioneId, @Param("adesso") Instant adesso);

	/**
	 * Cascata dalla tappa: cancella le domande ancora attive delle sue lezioni attive.
	 * Va chiamato PRIMA di cancellare le lezioni: la sottoquery vede solo le lezioni attive. Le domande delle
	 * lezioni già cancellate sono state cancellate insieme a loro.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Domanda d set d.deletedAt = :adesso
			where d.deletedAt is null
			and d.lezione.id in (select l.id from Lezione l where l.tappa.id = :tappaId)""")
	int cancellaDellaTappa(@Param("tappaId") UUID tappaId, @Param("adesso") Instant adesso);

}
