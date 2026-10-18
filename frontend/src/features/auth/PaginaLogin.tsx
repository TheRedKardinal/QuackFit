import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import type { DatiErrore } from '../../api/ApiError'
import CampoTesto from '../../common/CampoTesto'
import { messaggioErrore } from '../../common/messaggiErrore'
import { validaLogin, type DatiLogin, type ErroriCampi } from '../../common/validazione'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import { useAppDispatch } from '../../store/hooks'
import stili from './accesso.module.css'
import { accedi } from './authThunk'
import BarraAccesso from './BarraAccesso'
import TestoPulsante from '../../common/TestoPulsante'
import type { StatoVersoLogin } from '../../common/statoVersoLogin'
import { useInvioForm } from '../../common/useInvioForm'

type Campo = keyof DatiLogin
const ORDINE_CAMPI: Campo[] = ['email', 'password']

/**
 * Login (auth.md, Login). Nel frontend solo i campi obbligatori, niente regole della password.
 * Ogni login fallito mostra il messaggio unico (CREDENZIALI_NON_VALIDE), senza dire se è sbagliata l'email o la password.
 * Dopo il login le guardie portano alla schermata del codice (email non verificata) o alla mappa.
 */
function PaginaLogin() {
  const dispatch = useAppDispatch()
  const { invia, inviando, secondiAttesa, bloccato } = useInvioForm()
  const naviga = useNavigate()
  const posizione = useLocation()
  // dopo un reset riuscito arrivano un messaggio e l'email (mai codice o password)
  const statoIniziale = posizione.state as StatoVersoLogin | null
  const [conferma] = useState(statoIniziale?.messaggio ?? null)
  const [dati, setDati] = useState<DatiLogin>({ email: statoIniziale?.email ?? '', password: '' })
  const [errori, setErrori] = useState<ErroriCampi>({})
  const [erroreGenerale, setErroreGenerale] = useState<string | null>(null)
  const [mostraPassword, setMostraPassword] = useState(false)
  const campi = useRef<Partial<Record<Campo, HTMLInputElement | null>>>({})

  // letto lo state, lo si toglie dalla cronologia: con F5 il messaggio non riappare
  useEffect(() => {
    if (posizione.state) {
      naviga(posizione.pathname, { replace: true, state: null })
    }
  }, [naviga, posizione.pathname, posizione.state])

  function cambia(campo: Campo, valore: string) {
    setDati((precedenti) => ({ ...precedenti, [campo]: valore }))
    setErrori((precedenti) => {
      const altri = { ...precedenti }
      delete altri[campo]
      return altri
    })
  }

  function mostraErrori(nuovi: ErroriCampi) {
    setErrori(nuovi)
    const primo = ORDINE_CAMPI.find((campo) => nuovi[campo])
    if (primo) {
      campi.current[primo]?.focus()
    }
  }

  function gestisciErrore(errore: DatiErrore) {
    if (errore.codice === 'VALIDAZIONE' && Object.keys(errore.campi).length > 0) {
      mostraErrori(errore.campi)
    } else {
      setErroreGenerale(messaggioErrore(errore.codice))
    }
  }

  async function invio(evento: FormEvent) {
    evento.preventDefault()
    if (bloccato) {
      return
    }
    setErroreGenerale(null)
    const erroriLocali = validaLogin(dati)
    if (Object.keys(erroriLocali).length > 0) {
      mostraErrori(erroriLocali)
      return
    }
    const errore = await invia(() => dispatch(accedi(dati)).unwrap())
    if (errore) {
      gestisciErrore(errore)
    }
  }

  return (
    <main className={stili.pagina}>
      <BarraAccesso titolo="Login" />

      <div className={stili.centrato}>
        <h2 className={stili.titolo}>Bentornato su QuackFit!</h2>
        <p className={stili.sottotitolo}>Accedi per continuare la tua streak di apprendimento.</p>
      </div>

      {conferma && (
        <p role="status" className={stili.messaggioOk}>
          ✓ {conferma}
        </p>
      )}

      <Scheda>
        <form noValidate onSubmit={invio}>
          <CampoTesto
            id="email"
            etichetta="Email"
            tipo="email"
            valore={dati.email}
            onChange={(valore) => cambia('email', valore)}
            autoComplete="email"
            placeholder="esempio@email.com"
            errore={errori.email}
            ref={(elemento) => {
              campi.current.email = elemento
            }}
          />

          <CampoTesto
            id="password"
            etichetta="Password"
            tipo={mostraPassword ? 'text' : 'password'}
            valore={dati.password}
            onChange={(valore) => cambia('password', valore)}
            autoComplete="current-password"
            placeholder="Inserisci la tua password"
            errore={errori.password}
            accessorio={
              <Link to="/forgot-password" className={stili.link}>
                Password dimenticata?
              </Link>
            }
            ref={(elemento) => {
              campi.current.password = elemento
            }}
          >
            <button type="button" className={`${stili.mostra} mt-2`} onClick={() => setMostraPassword((m) => !m)}>
              {mostraPassword ? 'Nascondi password' : 'Mostra password'}
            </button>
          </CampoTesto>

          <MessaggioErrore testo={erroreGenerale} />

          <div className={stili.pulsantiera}>
            <Pulsante type="submit" disabled={bloccato}>
              <TestoPulsante testo="Accedi" inviando={inviando} secondiAttesa={secondiAttesa} />
            </Pulsante>
          </div>
        </form>
      </Scheda>

      <p className={stili.centrato}>Non hai ancora un account?</p>
      <div className={stili.pulsantiera}>
        <Link to="/register" className="btn-push btn-push-neutro text-center text-decoration-none">
          Registrati
        </Link>
      </div>
    </main>
  )
}

export default PaginaLogin
