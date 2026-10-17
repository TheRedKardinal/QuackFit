import { messaggioErrore } from '../../common/messaggiErrore'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import PulsanteEsci from './PulsanteEsci'
import { caricaProfilo } from './sessioneSlice'

/**
 * Il caricamento del profilo è fallito per un motivo diverso dal 401 (rete, 500): il token resta salvato.
 * "Riprova" è sempre manuale (nessun tentativo automatico); "Esci" è il logout volontario.
 */
function ErroreAvvio() {
  const dispatch = useAppDispatch()
  const errore = useAppSelector((stato) => stato.sessione.errore)

  return (
    <main className="container py-5 text-center" style={{ maxWidth: 480 }}>
      <p role="alert">{messaggioErrore(errore?.codice ?? 'ERRORE_INTERNO')}</p>
      <div className="d-grid gap-3">
        <button type="button" className="btn-push" onClick={() => void dispatch(caricaProfilo())}>
          Riprova
        </button>
        <PulsanteEsci />
      </div>
    </main>
  )
}

export default ErroreAvvio
