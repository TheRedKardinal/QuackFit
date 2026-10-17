import { Link } from 'react-router-dom'
import type { MeResponse } from '../../api/tipi'
import Icona from '../Icona'
import Avatar from './Avatar'
import stili from './layout.module.css'

interface Props {
  utente: MeResponse
  /** Nome della sezione corrente, sotto il marchio ("Percorso", "Profilo"). */
  sezione: string
}

/**
 * Barra in alto: marchio con la sezione, streak e avatar. La streak è il valore di /api/me,
 * già calcolato dal backend (0 se l'ultima attività non è né oggi né ieri): il frontend non la calcola.
 */
function BarraSuperiore({ utente, sezione }: Props) {
  const giorni = utente.streak.valore
  return (
    <header className={stili.barraSuperiore}>
      <div className={`${stili.colonna} ${stili.barraSuperioreInterno}`}>
        <Link to="/" className={stili.marchio}>
          <span className={stili.nomeMarchio}>QuackFit</span>
          <span className={stili.sezione}>{sezione}</span>
        </Link>
        <div className={stili.destra}>
          <span className={stili.streak} aria-label={`Streak: ${giorni} ${giorni === 1 ? 'giorno' : 'giorni'}`}>
            <span className={stili.iconaStreak}>
              <Icona nome="fiamma" dimensione={18} />
            </span>
            <span aria-hidden="true">{giorni}</span>
          </span>
          <Avatar utente={utente} />
        </div>
      </div>
    </header>
  )
}

export default BarraSuperiore
