import { NavLink } from 'react-router-dom'
import type { Ruolo } from '../../api/tipi'
import Icona, { type NomeIcona } from '../Icona'
import stili from './layout.module.css'

interface Voce {
  percorso: string
  testo: string
  icona: NomeIcona
}

const VOCI: Voce[] = [
  { percorso: '/', testo: 'Percorso', icona: 'percorso' },
  { percorso: '/profile', testo: 'Profilo', icona: 'profilo' },
]

/** Solo per ADMIN e SUPERADMIN: è comodità, i permessi veri li controlla il backend. */
const VOCE_ADMIN: Voce = { percorso: '/admin', testo: 'Admin', icona: 'admin' }

interface Props {
  ruolo: Ruolo
}

/** Barra in basso: Percorso e Profilo, più Admin per gli amministratori. La voce attiva ha aria-current="page". */
function BarraNavigazione({ ruolo }: Props) {
  const voci = ruolo === 'ADMIN' || ruolo === 'SUPERADMIN' ? [...VOCI, VOCE_ADMIN] : VOCI
  return (
    <nav className={stili.barraNavigazione} aria-label="Navigazione principale">
      <ul className={`${stili.colonna} ${stili.voci}`}>
        {voci.map((voce) => (
          <li key={voce.percorso} className={stili.voce}>
            <NavLink
              to={voce.percorso}
              end={voce.percorso === '/'}
              className={({ isActive }) => `${stili.link} ${isActive ? stili.linkAttivo : ''}`}
            >
              <Icona nome={voce.icona} />
              {voce.testo}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}

export default BarraNavigazione
