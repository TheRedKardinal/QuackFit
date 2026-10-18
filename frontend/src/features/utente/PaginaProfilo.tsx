import { Link } from 'react-router-dom'
import type { Ruolo } from '../../api/tipi'
import FotoProfilo from '../../common/FotoProfilo'
import Icona from '../../common/Icona'
import PulsanteEsci from '../../common/PulsanteEsci'
import Scheda from '../../common/Scheda'
import { useAppSelector } from '../../store/hooks'
import stili from './profilo.module.css'

const ETICHETTA_RUOLO: Record<Ruolo, string> = {
  UTENTE: 'Utente',
  ADMIN: 'Admin',
  SUPERADMIN: 'Superadmin',
}

/**
 * Profilo (GET /api/me, già caricato dalla sessione): avatar, nome, email, ruolo e SOLO tre metriche
 * (streak attuale, record, lezioni completate). Livelli, XP, badge e "Membro da" della schermata Stitch sono fuori
 * perimetro. `emailVerificata` non si mostra. La streak è quella di /api/me, già calcolata dal backend.
 */
function PaginaProfilo() {
  const utente = useAppSelector((stato) => stato.sessione.utente)
  if (!utente) {
    return null
  }

  return (
    <section>
      <Scheda>
        <div className={stili.intestazione}>
          <FotoProfilo utente={utente} dimensione={6} />
          <h1 className={stili.nome}>
            {utente.nome} {utente.cognome}
          </h1>
          <p className={stili.email}>{utente.email}</p>
          <span className={stili.ruolo}>{ETICHETTA_RUOLO[utente.ruolo]}</span>
        </div>
      </Scheda>

      <h2 className={stili.titoloSezione}>Le tue statistiche</h2>
      <dl className={stili.metriche}>
        <div className={stili.metrica}>
          <dt className={stili.etichettaMetrica}>Streak attuale</dt>
          <dd className={stili.valore}>
            <span className={stili.iconaStreak}>
              <Icona nome="fiamma" dimensione={20} />
            </span>{' '}
            {utente.streak.valore}
          </dd>
        </div>
        <div className={stili.metrica}>
          <dt className={stili.etichettaMetrica}>Record</dt>
          <dd className={stili.valore}>{utente.streak.record}</dd>
        </div>
        <div className={stili.metrica}>
          <dt className={stili.etichettaMetrica}>Lezioni completate</dt>
          <dd className={stili.valore}>{utente.lezioniCompletate}</dd>
        </div>
      </dl>

      <div className={stili.azioni}>
        <Link to="/profile/edit" className="btn-push text-center text-decoration-none">
          Modifica profilo
        </Link>
        <PulsanteEsci />
      </div>
    </section>
  )
}

export default PaginaProfilo
