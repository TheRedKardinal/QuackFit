import { useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import CampoTesto from '../../common/CampoTesto'
import { messaggioErrore } from '../../common/messaggiErrore'
import { pulisciEmail, validaPasswordDimenticata } from '../../common/validazione'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import { useAppDispatch } from '../../store/hooks'
import stili from './accesso.module.css'
import { passwordDimenticata } from './authThunk'
import BarraAccesso from './BarraAccesso'
import TestoPulsante from './TestoPulsante'
import { useInvioForm } from './useInvioForm'

/** Stesso testo della risposta del backend: non rivela se l'email è registrata. */
export const MESSAGGIO_PASSWORD_DIMENTICATA = "Se l'email è registrata, riceverai un codice per reimpostare la password."

/**
 * Password dimenticata (auth.md): si chiede il codice e il messaggio è sempre lo stesso, che l'email esista o no.
 * Poi si prosegue alla reimpostazione con l'email già compilata (nello state del router passa solo l'email).
 */
function PaginaPasswordDimenticata() {
  const dispatch = useAppDispatch()
  const naviga = useNavigate()
  const { invia, inviando, secondiAttesa, bloccato } = useInvioForm()
  const [email, setEmail] = useState('')
  const [erroreEmail, setErroreEmail] = useState<string | undefined>()
  const [erroreGenerale, setErroreGenerale] = useState<string | null>(null)
  const [inviata, setInviata] = useState(false)
  const campoEmail = useRef<HTMLInputElement>(null)

  async function invio(evento: FormEvent) {
    evento.preventDefault()
    if (bloccato) {
      return
    }
    setErroreGenerale(null)
    const errori = validaPasswordDimenticata(email)
    if (errori.email) {
      setErroreEmail(errori.email)
      campoEmail.current?.focus()
      return
    }
    const errore = await invia(() => dispatch(passwordDimenticata({ email })).unwrap())
    if (!errore) {
      setInviata(true)
    } else if (errore.campi.email) {
      setErroreEmail(errore.campi.email)
      campoEmail.current?.focus()
    } else {
      setErroreGenerale(messaggioErrore(errore.codice))
    }
  }

  function vaiAlCodice() {
    naviga('/reset-password', { state: { email: pulisciEmail(email) } })
  }

  return (
    <main className={stili.pagina}>
      <BarraAccesso titolo="Password dimenticata" />

      <div className={stili.centrato}>
        <h2 className={stili.titolo}>Recupera l'accesso</h2>
        <p className={stili.sottotitolo}>Scrivi l'email del tuo account: ti invieremo un codice per scegliere una nuova password.</p>
      </div>

      <Scheda>
        <form noValidate onSubmit={invio}>
          <CampoTesto
            id="email"
            etichetta="Email"
            tipo="email"
            valore={email}
            onChange={(valore) => {
              setEmail(valore)
              setErroreEmail(undefined)
              setInviata(false)
            }}
            autoComplete="email"
            placeholder="esempio@email.com"
            errore={erroreEmail}
            ref={campoEmail}
          />

          {inviata && (
            <p role="status" className={stili.messaggioOk}>
              {MESSAGGIO_PASSWORD_DIMENTICATA}
            </p>
          )}
          <MessaggioErrore testo={erroreGenerale} />

          <div className={stili.pulsantiera}>
            {inviata ? (
              <Pulsante variante="successo" onClick={vaiAlCodice}>
                Inserisci il codice
              </Pulsante>
            ) : (
              <Pulsante type="submit" disabled={bloccato}>
                <TestoPulsante testo="Invia il codice" inviando={inviando} secondiAttesa={secondiAttesa} />
              </Pulsante>
            )}
          </div>
        </form>
      </Scheda>

      <p className={stili.centrato}>
        <Link to="/reset-password" className={stili.link}>
          Ho già un codice
        </Link>
        {' · '}
        <Link to="/login" className={stili.link}>
          Torna al login
        </Link>
      </p>
    </main>
  )
}

export default PaginaPasswordDimenticata
