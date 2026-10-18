import { useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import type { DatiErrore } from '../../api/ApiError'
import CampoTesto from '../../common/CampoTesto'
import CodiceSeiCifre, { type CodiceSeiCifreHandle } from '../../common/CodiceSeiCifre'
import { messaggioErrore } from '../../common/messaggiErrore'
import { SECONDI_ATTESA_429 } from '../../common/useConteggioAllaRovescia'
import {
  pulisciEmail,
  validaPasswordDimenticata,
  validaReimpostazione,
  type DatiReimpostazione,
  type ErroriCampi,
} from '../../common/validazione'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import { useAppDispatch } from '../../store/hooks'
import stili from './accesso.module.css'
import { passwordDimenticata, reimpostaPassword } from './authThunk'
import BarraAccesso from './BarraAccesso'
import { MESSAGGIO_PASSWORD_DIMENTICATA } from './PaginaPasswordDimenticata'
import RiquadroRequisiti from '../../common/RiquadroRequisiti'
import TestoPulsante from '../../common/TestoPulsante'
import { MESSAGGIO_PASSWORD_AGGIORNATA, type StatoVersoLogin } from '../../common/statoVersoLogin'
import { useInvioForm } from '../../common/useInvioForm'

type Campo = keyof DatiReimpostazione
const ORDINE_CAMPI: Campo[] = ['email', 'codice', 'nuovaPassword']

/**
 * Reimpostazione della password con il codice (auth.md, Reimpostazione password).
 * Ogni codice rifiutato risponde allo stesso modo (CODICE_NON_VALIDO), per non rivelare se l'email è registrata.
 * Se riesce: il backend fa logout ovunque, il thunk cancella un eventuale token salvato e si va al login.
 */
function PaginaReimpostaPassword() {
  const dispatch = useAppDispatch()
  const naviga = useNavigate()
  const posizione = useLocation()
  const emailIniziale = (posizione.state as { email?: string } | null)?.email ?? ''
  const reimposta = useInvioForm()
  const reinvio = useInvioForm()
  const [dati, setDati] = useState<DatiReimpostazione>({ email: emailIniziale, codice: '', nuovaPassword: '' })
  const [errori, setErrori] = useState<ErroriCampi>({})
  const [avviso, setAvviso] = useState<{ tipo: 'ok' | 'errore'; testo: string } | null>(null)
  const [mostraPassword, setMostraPassword] = useState(false)
  const campoEmail = useRef<HTMLInputElement>(null)
  const campoCodice = useRef<CodiceSeiCifreHandle>(null)
  const campoPassword = useRef<HTMLInputElement>(null)


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
    if (primo === 'email') {
      campoEmail.current?.focus()
    } else if (primo === 'codice') {
      campoCodice.current?.focus()
    } else if (primo === 'nuovaPassword') {
      campoPassword.current?.focus()
    }
  }

  function gestisciErrore(errore: DatiErrore) {
    if (errore.codice === 'VALIDAZIONE' && Object.keys(errore.campi).length > 0) {
      mostraErrori(errore.campi)
    } else if (errore.codice === 'CODICE_NON_VALIDO') {
      // codice svuotato per riprovare: il cursore torna sulla prima casella
      setDati((precedenti) => ({ ...precedenti, codice: '' }))
      setErrori({ codice: messaggioErrore(errore.codice) })
      campoCodice.current?.focus(true)
    } else {
      setAvviso({ tipo: 'errore', testo: messaggioErrore(errore.codice) })
    }
  }

  async function invio(evento: FormEvent) {
    evento.preventDefault()
    if (reimposta.bloccato) {
      return
    }
    setAvviso(null)
    const erroriLocali = validaReimpostazione(dati)
    if (Object.keys(erroriLocali).length > 0) {
      mostraErrori(erroriLocali)
      return
    }
    const errore = await reimposta.invia(() => dispatch(reimpostaPassword(dati)).unwrap())
    if (errore) {
      gestisciErrore(errore)
      return
    }
    const stato: StatoVersoLogin = { messaggio: MESSAGGIO_PASSWORD_AGGIORNATA, email: pulisciEmail(dati.email) }
    naviga('/login', { replace: true, state: stato })
  }

  /** Nuovo codice: richiama la password dimenticata (sempre 200), poi 60 secondi di attesa. */
  async function nuovoCodice() {
    setAvviso(null)
    const erroreEmail = validaPasswordDimenticata(dati.email)
    if (erroreEmail.email) {
      mostraErrori(erroreEmail)
      return
    }
    const errore = await reinvio.invia(() => dispatch(passwordDimenticata({ email: dati.email })).unwrap())
    if (errore) {
      setAvviso({ tipo: 'errore', testo: messaggioErrore(errore.codice) })
    } else {
      reinvio.avviaAttesa(SECONDI_ATTESA_429)
      setAvviso({ tipo: 'ok', testo: MESSAGGIO_PASSWORD_DIMENTICATA })
    }
  }

  return (
    <main className={stili.pagina}>
      <BarraAccesso titolo="Nuova password" />

      <div className={stili.centrato}>
        <h2 className={stili.titolo}>Scegli una nuova password</h2>
        <p className={stili.sottotitolo}>Inserisci il codice a 6 cifre che hai ricevuto via email e la nuova password.</p>
      </div>

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
            ref={campoEmail}
          />

          <CodiceSeiCifre
            id="codice"
            etichetta="Codice"
            valore={dati.codice}
            onChange={(valore) => cambia('codice', valore)}
            errore={errori.codice}
            ref={campoCodice}
          />

          <CampoTesto
            id="nuovaPassword"
            etichetta="Nuova password"
            tipo={mostraPassword ? 'text' : 'password'}
            valore={dati.nuovaPassword}
            onChange={(valore) => cambia('nuovaPassword', valore)}
            autoComplete="new-password"
            errore={errori.nuovaPassword}
            accessorio={
              <button type="button" className={stili.mostra} onClick={() => setMostraPassword((m) => !m)}>
                {mostraPassword ? 'Nascondi' : 'Mostra'}
              </button>
            }
            ref={campoPassword}
          />

          <RiquadroRequisiti password={dati.nuovaPassword} />

          {avviso && (
            <p
              role={avviso.tipo === 'errore' ? 'alert' : 'status'}
              className={avviso.tipo === 'ok' ? stili.messaggioOk : stili.erroreGenerale}
            >
              {avviso.testo}
            </p>
          )}

          <div className={stili.pulsantiera}>
            <Pulsante type="submit" disabled={reimposta.bloccato}>
              <TestoPulsante
                testo="Salva la nuova password"
                inviando={reimposta.inviando}
                secondiAttesa={reimposta.secondiAttesa}
              />
            </Pulsante>
            <Pulsante variante="neutro" onClick={nuovoCodice} disabled={reinvio.bloccato}>
              <TestoPulsante testo="Invia di nuovo il codice" inviando={reinvio.inviando} secondiAttesa={reinvio.secondiAttesa} />
            </Pulsante>
          </div>
        </form>
      </Scheda>

      <p className={stili.centrato}>
        <Link to="/login" className={stili.link}>
          Torna al login
        </Link>
      </p>
    </main>
  )
}

export default PaginaReimpostaPassword
