package it.quackfit.tentativi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import it.quackfit.contenuti.TipoDomanda;

/**
 * Query del resoconto di un tentativo (vedi tentativi.md, Resoconto). Devono raggiungere anche lezioni, domande,
 * opzioni ed elementi cancellati dopo il tentativo, quindi:
 * - SQL nativo, che non passa da @SQLRestriction (JPQL e relazioni JPA nasconderebbero i cancellati);
 * - NamedParameterJdbcTemplate e non @Query nativa di Spring Data: lì i tipi li sceglie Hibernate, e su H2 gli UUID
 *   escono come byte[]. Qui li legge il RowMapper, uguali su H2 e PostgreSQL:
 *   UUID con getObject(..., UUID.class), date come OffsetDateTime poi toInstant(), colonne per alias (H2 li restituisce
 *   in maiuscolo, PostgreSQL in minuscolo: la ricerca per nome non distingue).
 *
 * Il proprietario si controlla dentro ogni query (t.utente_id): un tentativo altrui dà un risultato vuoto (404).
 * Parametri sempre con nome (:tentativoId), mai concatenati.
 */
@Repository
public class ResocontoQuery {

	private static final String INTESTAZIONE = """
			select t.id as tentativo_id, t.esito as esito, t.errori as errori,
				t.iniziato_at as iniziato_at, t.finito_at as finito_at,
				l.id as lezione_id, l.titolo as lezione_titolo
			from tentativo_lezione t
			join lezione l on l.id = t.lezione_id
			where t.id = :tentativoId and t.utente_id = :utenteId""";

	private static final String RIGHE_OPZIONI = """
			select d.id as domanda_id, d.ordine as ordine, d.testo as testo, d.tipo as tipo,
				d.spiegazione as spiegazione,
				o.id as opzione_id, o.testo as opzione_testo, o.corretta as corretta,
				g.id as soluzione_id, g.testo as soluzione_testo
			from risposta_opzione r
			join tentativo_lezione t on t.id = r.tentativo_id
			join domanda d on d.id = r.domanda_id
			join opzione o on o.id = r.opzione_id
			join opzione g on g.domanda_id = d.id and g.corretta = true
			where r.tentativo_id = :tentativoId and t.utente_id = :utenteId
			order by d.ordine""";

	private static final String RIGHE_ABBINAMENTI = """
			select d.id as domanda_id, d.ordine as ordine, d.testo as testo, d.tipo as tipo,
				d.spiegazione as spiegazione,
				s.id as sinistro_id, s.testo as sinistro_testo,
				x.id as destro_id, x.testo as destro_testo,
				g.id as soluzione_id, g.testo as soluzione_testo
			from risposta_abbinamento r
			join tentativo_lezione t on t.id = r.tentativo_id
			join domanda d on d.id = r.domanda_id
			join elemento_abbinamento s on s.id = r.elemento_sinistro_id
			join elemento_abbinamento x on x.id = r.elemento_destro_id
			join elemento_abbinamento g on g.id = s.abbinamento_corretto_id
			where r.tentativo_id = :tentativoId and t.utente_id = :utenteId
			order by d.ordine, s.ordine""";

	private final NamedParameterJdbcTemplate jdbc;

	public ResocontoQuery(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Il tentativo con la sua lezione; vuoto se non esiste o non è dell'utente. */
	public Optional<IntestazioneResoconto> intestazione(UUID tentativoId, UUID utenteId) {
		return jdbc.query(INTESTAZIONE, parametri(tentativoId, utenteId), (rs, riga) -> new IntestazioneResoconto(
				uuid(rs, "tentativo_id"),
				EsitoTentativo.valueOf(rs.getString("esito")),
				rs.getInt("errori"),
				istante(rs, "iniziato_at"),
				istante(rs, "finito_at"),
				uuid(rs, "lezione_id"),
				rs.getString("lezione_titolo")))
				.stream().findFirst();
	}

	/** Domande con opzioni già risposte, nell'ordine dell'admin, con prima risposta e soluzione. */
	public List<RigaResocontoOpzione> righeOpzioni(UUID tentativoId, UUID utenteId) {
		return jdbc.query(RIGHE_OPZIONI, parametri(tentativoId, utenteId), (rs, riga) -> new RigaResocontoOpzione(
				uuid(rs, "domanda_id"),
				rs.getInt("ordine"),
				rs.getString("testo"),
				TipoDomanda.valueOf(rs.getString("tipo")),
				rs.getString("spiegazione"),
				uuid(rs, "opzione_id"),
				rs.getString("opzione_testo"),
				rs.getBoolean("corretta"),
				uuid(rs, "soluzione_id"),
				rs.getString("soluzione_testo")));
	}

	/** Coppie della prima risposta agli abbinamenti: domande nell'ordine dell'admin, coppie nell'ordine dei sinistri. */
	public List<RigaResocontoAbbinamento> righeAbbinamenti(UUID tentativoId, UUID utenteId) {
		return jdbc.query(RIGHE_ABBINAMENTI, parametri(tentativoId, utenteId), (rs, riga) -> new RigaResocontoAbbinamento(
				uuid(rs, "domanda_id"),
				rs.getInt("ordine"),
				rs.getString("testo"),
				TipoDomanda.valueOf(rs.getString("tipo")),
				rs.getString("spiegazione"),
				uuid(rs, "sinistro_id"),
				rs.getString("sinistro_testo"),
				uuid(rs, "destro_id"),
				rs.getString("destro_testo"),
				uuid(rs, "soluzione_id"),
				rs.getString("soluzione_testo")));
	}

	private static Map<String, Object> parametri(UUID tentativoId, UUID utenteId) {
		return Map.of("tentativoId", tentativoId, "utenteId", utenteId);
	}

	private static UUID uuid(ResultSet rs, String colonna) throws SQLException {
		return rs.getObject(colonna, UUID.class);
	}

	/** timestamp with time zone → OffsetDateTime → Instant (null resta null). Mai getTimestamp, che usa il fuso locale. */
	private static Instant istante(ResultSet rs, String colonna) throws SQLException {
		OffsetDateTime valore = rs.getObject(colonna, OffsetDateTime.class);
		return valore == null ? null : valore.toInstant();
	}

}
