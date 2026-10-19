package it.quackfit.tentativi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TentativoLezioneRepository extends JpaRepository<TentativoLezione, UUID> {

	/** Il tentativo solo se è dell'utente: un tentativo altrui risulta inesistente (404). */
	Optional<TentativoLezione> findByIdAndUtenteId(UUID id, UUID utenteId);

	/**
	 * Come {@link #findByIdAndUtenteId}, ma con un lock pessimistico (SELECT ... FOR UPDATE) fino alla fine della
	 * transazione: le risposte allo stesso tentativo vanno in fila, così errori e prima risposta restano coerenti anche
	 * con due richieste quasi insieme (vedi backend/CLAUDE.md, Regole ricorrenti, Contatori).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from TentativoLezione t where t.id = :id and t.utente.id = :utenteId")
	Optional<TentativoLezione> bloccaDellUtente(@Param("id") UUID id, @Param("utenteId") UUID utenteId);

	/** Tentativi di un utente su una lezione in un certo stato (es. gli IN_CORSO da chiudere all'avvio). */
	List<TentativoLezione> findByUtenteIdAndLezioneIdAndEsito(UUID utenteId, UUID lezioneId, EsitoTentativo esito);

	/**
	 * Id delle lezioni attive con almeno un tentativo SUPERATO dell'utente (le lezioni COMPLETATE di percorso.md).
	 * Il join con Lezione passa da @SQLRestriction: le lezioni cancellate restano fuori da sole.
	 */
	@Query("""
			select distinct l.id from TentativoLezione t join t.lezione l
			where t.utente.id = :utenteId and t.esito = it.quackfit.tentativi.EsitoTentativo.SUPERATO""")
	List<UUID> lezioniCompletate(@Param("utenteId") UUID utenteId);

}
