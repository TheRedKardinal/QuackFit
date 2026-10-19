import { Link, NavLink, Outlet } from 'react-router-dom'
import Icona, { type NomeIcona } from '../../common/Icona'
import { useAppSelector } from '../../store/hooks'
import stili from './admin.module.css'

interface Voce {
  percorso: string
  testo: string
  icona: NomeIcona
}

/** Le sezioni del pannello, scritte una volta sola: sidebar su desktop, barra in basso su mobile. */
const VOCI: Voce[] = [
  { percorso: '/admin/users', testo: 'Utenti', icona: 'utenti' },
  { percorso: '/admin/content', testo: 'Contenuti', icona: 'contenuti' },
]

function Voci({ classe }: { classe: string }) {
  return (
    <ul className={classe}>
      {VOCI.map((voce) => (
        <li key={voce.percorso}>
          <NavLink to={voce.percorso} className={({ isActive }) => `${stili.link} ${isActive ? stili.linkAttivo : ''}`}>
            <Icona nome={voce.icona} />
            {voce.testo}
          </NavLink>
        </li>
      ))}
    </ul>
  )
}

/**
 * Guscio del pannello admin, fuori da LayoutApp: barra in alto con "Torna all'app", sidebar Utenti/Contenuti da lg in
 * su e la stessa lista come barra in basso sotto lg. Le due versioni le sceglie il CSS di Bootstrap (d-none, d-lg-…),
 * senza codice che misura lo schermo. Il link attivo ha aria-current="page" (NavLink), grassetto e una barretta.
 * Sta dentro RichiedeAdmin: chi arriva qui è ADMIN o SUPERADMIN (comodità, il backend decide).
 */
function LayoutAdmin() {
  const utente = useAppSelector((stato) => stato.sessione.utente)
  if (!utente) {
    return null
  }

  return (
    <div className={stili.pannello}>
      <header className={stili.barraSuperiore}>
        <div className={stili.barraSuperioreInterno}>
          <div className={stili.marchio}>
            <span className={stili.nomeMarchio}>QuackFit</span>
            <span className={stili.etichettaAdmin}>Admin</span>
          </div>
          <Link to="/" className={stili.tornaApp}>
            <Icona nome="indietro" dimensione={20} />
            Torna all&apos;app
          </Link>
        </div>
      </header>

      <nav className={`${stili.sidebar} d-none d-lg-block`} aria-label="Sezioni admin">
        <Voci classe={stili.vociSidebar} />
      </nav>

      <main className={stili.contenuto}>
        <Outlet />
      </main>

      <nav className={`${stili.barraInBasso} d-lg-none`} aria-label="Sezioni admin">
        <Voci classe={stili.vociBarra} />
      </nav>
    </div>
  )
}

export default LayoutAdmin
