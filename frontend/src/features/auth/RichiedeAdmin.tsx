import { Navigate, Outlet } from 'react-router-dom'
import { useAppSelector } from '../../store/hooks'

/**
 * Terza guardia: solo ADMIN e SUPERADMIN. È solo comodità: i permessi veri li controlla il backend
 * (le chiamate admin di un non-admin rispondono comunque 403 ACCESSO_NEGATO).
 */
function RichiedeAdmin() {
  const ruolo = useAppSelector((stato) => stato.sessione.utente?.ruolo)

  if (ruolo !== 'ADMIN' && ruolo !== 'SUPERADMIN') {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}

export default RichiedeAdmin
