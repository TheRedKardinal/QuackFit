import { useEffect } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '../store/hooks'
import { logout } from '../store/sessione/sessioneSlice'
import type { StatoVersoLogin } from './statoVersoLogin'

/**
 * Rotta tecnica /logout, FUORI dalle guardie: fa il logout e poi porta al login passando lo stesso state
 * (messaggio ed email, mai password). Serve dopo le operazioni che fanno logout ovunque (cambio password, conferma
 * del cambio email): se il logout partisse dalla pagina protetta, la sua guardia porterebbe al login da sola e senza
 * state, e il messaggio andrebbe perso.
 */
function Uscita() {
  const dispatch = useAppDispatch()
  const token = useAppSelector((stato) => stato.sessione.token)
  const stato = useLocation().state as StatoVersoLogin | null

  useEffect(() => {
    dispatch(logout())
  }, [dispatch])

  if (token) {
    return null
  }
  return <Navigate to="/login" replace state={stato} />
}

export default Uscita
