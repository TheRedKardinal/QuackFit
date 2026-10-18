import { useEffect } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import ErroreAvvio from './ErroreAvvio'
import SchermataCaricamento from './SchermataCaricamento'
import { caricaProfilo } from '../../store/sessione/sessioneSlice'

/**
 * Prima guardia: senza token si va al login. Con il token si carica /api/me e, finché non è riuscito,
 * non si mostra nessun contenuto protetto (solo caricamento o errore con Riprova/Esci).
 */
function RichiedeAutenticazione() {
  const dispatch = useAppDispatch()
  const { token, utente, caricamento } = useAppSelector((stato) => stato.sessione)

  useEffect(() => {
    if (token && caricamento === 'inattivo') {
      void dispatch(caricaProfilo())
    }
  }, [dispatch, token, caricamento])

  if (!token) {
    return <Navigate to="/login" replace />
  }
  if (caricamento === 'fallito') {
    return <ErroreAvvio />
  }
  if (caricamento !== 'riuscito' || !utente) {
    return <SchermataCaricamento />
  }
  return <Outlet />
}

export default RichiedeAutenticazione
