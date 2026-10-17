import { Navigate, Outlet } from 'react-router-dom'
import { useAppSelector } from '../../store/hooks'

/** Pagine pubbliche (login, registrazione, password dimenticata): chi ha già un token va alla home. */
function SoloOspite() {
  const token = useAppSelector((stato) => stato.sessione.token)

  if (token) {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}

export default SoloOspite
