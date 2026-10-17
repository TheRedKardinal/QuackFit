import { Navigate, Outlet } from 'react-router-dom'
import { useAppSelector } from '../../store/hooks'

/** Seconda guardia (dentro RichiedeAutenticazione, quindi il profilo è già caricato): email non verificata → codice. */
function RichiedeEmailVerificata() {
  const emailVerificata = useAppSelector((stato) => stato.sessione.utente?.emailVerificata)

  if (!emailVerificata) {
    return <Navigate to="/verify-email" replace />
  }
  return <Outlet />
}

export default RichiedeEmailVerificata
