import { Outlet, useLocation } from 'react-router-dom'
import { useAppSelector } from '../../store/hooks'
import BarraNavigazione from './BarraNavigazione'
import BarraSuperiore from './BarraSuperiore'
import stili from './layout.module.css'

/** Nome della sezione mostrato nella barra in alto, in base al primo pezzo del percorso. */
function nomeSezione(percorso: string): string {
  if (percorso.startsWith('/profile')) {
    return 'Profilo'
  }
  if (percorso.startsWith('/admin')) {
    return 'Admin'
  }
  return 'Percorso'
}

/**
 * Guscio delle pagine dell'app (utente autenticato con email verificata): barra in alto, contenuto in una colonna
 * centrata e barra in basso. Sta dentro le guardie, quindi il profilo è già caricato.
 */
function LayoutApp() {
  const utente = useAppSelector((stato) => stato.sessione.utente)
  const { pathname } = useLocation()

  if (!utente) {
    return null
  }

  return (
    <div className={stili.app}>
      <BarraSuperiore utente={utente} sezione={nomeSezione(pathname)} />
      <main className={`${stili.colonna} ${stili.contenuto}`}>
        <Outlet />
      </main>
      <BarraNavigazione ruolo={utente.ruolo} />
    </div>
  )
}

export default LayoutApp
