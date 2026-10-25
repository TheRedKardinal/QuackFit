import { Link, Outlet } from 'react-router-dom'
import stili from './accesso.module.css'

/**
 * Guscio comune delle pagine prima dell'accesso (login, registrazione, recupero e reimpostazione della password,
 * verifica email): la pagina e sotto la riga del copyright, scritta qui una volta sola. È una rotta di layout senza
 * percorso, usata sotto SoloOspite e sotto RichiedeAutenticazione (solo per /verify-email): i percorsi non cambiano.
 */
function LayoutAccesso() {
  return (
    <>
      <Outlet />
      <footer className={stili.copyright}>
        © 2026 Claudio Giasi · Tutti i diritti riservati ·{' '}
        <Link to="/privacy" className={stili.linkPiede}>
          Privacy e Termini
        </Link>
      </footer>
    </>
  )
}

export default LayoutAccesso
