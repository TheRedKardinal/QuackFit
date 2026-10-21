import { useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import type { DatiErrore } from '../../api/ApiError'
import CampoTesto from '../../common/CampoTesto'
import CodiceSeiCifre, { type CodiceSeiCifreHandle } from '../../common/CodiceSeiCifre'
import { messaggioErrore } from '../../common/messaggiErrore'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import { MESSAGGIO_EMAIL_AGGIORNATA, type StatoVersoLogin } from '../../common/statoVersoLogin'
import TestoPulsante from '../../common/TestoPulsante'
import { SECONDI_ATTESA_429 } from '../../common/useConteggioAllaRovescia'
import { useInvioForm } from '../../common/useInvioForm'
import {
  pulisciEmail,
  validaCambioEmail,
  validaCodice,
  type DatiCambioEmail,
  type ErroriCampi,
} from '../../common/validazione'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import stili from './profilo.module.css'
import { confermaCambioEmail, richiediCambioEmail } from './utenteThunk'

type Campo = keyof DatiCambioEmail
const ORDINE_CAMPI: Campo[] = ['nuovaEmail', 'password']

/**
 * Cambio email in due passaggi (me.md, Cambio email):
 * 1. nuova email e password → il codice parte verso la NUOVA email ("Invia di nuovo" dopo 60 secondi);
 * 2. codice → 204 senza nuovo token e logout ovunque: si va a /logout e poi al login con il messaggio e la nuova email.
 * L'email dell'utente cambia solo con la conferma.
 */
function SezioneCambioEmail() {
  const dispatch = useAppDispatch()
  const naviga = useNavigate()
  const emailAttuale = useAppSelector((stato) => stato.sessione.utente?.email ?? '')
  const richiesta = useInvioForm()
  const reinvio = useInvioForm()
  const conferma = useInvioForm()
  const [passo, setPasso] = useState<'richiesta' | 'codice'>('richiesta')
  const [dati, setDati] = useState<DatiCambioEmail>({ nuovaEmail: '', password: '' })
  const [errori, setErrori] = useState<ErroriCampi>({})
  const [codice, setCodice] = useState('')
  const [erroreCodice, setErroreCodice] = useState<string | undefined>()
  const [avviso, setAvviso] = useState<{ tipo: 'ok' | 'errore'; testo: string } | null>(null)
  const [mostraPassword, setMostraPassword] = useState(false)
  const campi = useRef<Partial<Record<Campo, HTMLInputElement | null>>>({})
  const campoCodice = useRef<CodiceSeiCifreHandle>(null)

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

  function erroreRichiesta(errore: DatiErrore) {
    if (errore.codice === 'VALIDAZIONE' && Object.keys(errore.campi).length > 0) {
      mostraErrori(errore.campi)
    } else if (errore.codice === 'PASSWORD_ATTUALE_ERRATA') {
      mostraErrori({ password: messaggioErrore(errore.codice) })
    } else if (errore.codice === 'EMAIL_GIA_USATA') {
      mostraErrori({ nuovaEmail: messaggioErrore(errore.codice) })
    } else {
      setAvviso({ tipo: 'errore', testo: messaggioErrore(errore.codice) })
    }
  }

  async function invioRichiesta(evento: FormEvent) {
    evento.preventDefault()
    if (richiesta.bloccato) {
      return
    }
    setAvviso(null)
    const erroriLocali = validaCambioEmail(dati, emailAttuale)
    if (Object.keys(erroriLocali).length > 0) {
      mostraErrori(erroriLocali)
      return
    }
    const errore = await richiesta.invia(() => dispatch(richiediCambioEmail(dati)).unwrap())
    if (errore) {
      erroreRichiesta(errore)
      return
    }
    // il codice è appena partito: "Invia di nuovo" resta disattivato per 60 secondi
    reinvio.avviaAttesa(SECONDI_ATTESA_429)
    setPasso('codice')
  }

  async function nuovoCodice() {
    setAvviso(null)
    const errore = await reinvio.invia(() => dispatch(richiediCambioEmail(dati)).unwrap())
    if (errore) {
      setAvviso({ tipo: 'errore', testo: messaggioErrore(errore.codice) })
    } else {
      reinvio.avviaAttesa(SECONDI_ATTESA_429)
      setErroreCodice(undefined)
      setAvviso({ tipo: 'ok', testo: 'Ti abbiamo inviato un nuovo codice.' })
    }
  }

  async function invioCodice(evento: FormEvent) {
    evento.preventDefault()
    if (conferma.bloccato) {
      return
    }
    setAvviso(null)
    const erroreLocale = validaCodice(codice).codice
    if (erroreLocale) {
      setErroreCodice(erroreLocale)
      campoCodice.current?.focus()
      return
    }
    const errore = await conferma.invia(() => dispatch(confermaCambioEmail({ codice })).unwrap())
    if (!errore) {
      const stato: StatoVersoLogin = { messaggio: MESSAGGIO_EMAIL_AGGIORNATA, email: pulisciEmail(dati.nuovaEmail) }
      naviga('/logout', { replace: true, state: stato })
    } else if (errore.codice === 'EMAIL_GIA_USATA') {
      // presa nel frattempo da un altro account: il codice è ormai usato, si riparte dalla richiesta
      setPasso('richiesta')
      setCodice('')
      mostraErrori({ nuovaEmail: messaggioErrore(errore.codice) })
    } else {
      // CODICE_NON_VALIDO, CODICE_SCADUTO, TROPPI_TENTATIVI: caselle svuotate per riprovare
      setErroreCodice(errore.campi.codice ?? messaggioErrore(errore.codice))
      setCodice('')
      campoCodice.current?.focus(true)
    }
  }

  function altraEmail() {
    setPasso('richiesta')
    setCodice('')
    setErroreCodice(undefined)
    setAvviso(null)
  }

  const messaggioAvviso =
    avviso?.tipo === 'ok' ? (
      <p role="status" className={stili.messaggioOk}>
        {avviso.testo}
      </p>
    ) : (
      <MessaggioErrore testo={avviso?.testo} />
    )

  return (
    <Scheda>
      <h2 className={stili.titoloSezione}>Cambio email</h2>
      {passo === 'richiesta' ? (
        <form noValidate onSubmit={invioRichiesta}>
          <CampoTesto
            id="nuovaEmail"
            etichetta="Nuova email"
            tipo="email"
            valore={dati.nuovaEmail}
            onChange={(valore) => cambia('nuovaEmail', valore)}
            autoComplete="email"
            placeholder="nuova.email@esempio.it"
            errore={errori.nuovaEmail}
            ref={(elemento) => {
              campi.current.nuovaEmail = elemento
            }}
          />
          <CampoTesto
            id="passwordCambioEmail"
            etichetta="Password attuale di conferma"
            tipo={mostraPassword ? 'text' : 'password'}
            valore={dati.password}
            onChange={(valore) => cambia('password', valore)}
            autoComplete="current-password"
            errore={errori.password}
            accessorio={
              <button type="button" className={stili.mostra} onClick={() => setMostraPassword((m) => !m)}>
                {mostraPassword ? 'Nascondi' : 'Mostra'}
              </button>
            }
            ref={(elemento) => {
              campi.current.password = elemento
            }}
          />
          <p className={stili.nota}>
            Ti invieremo un codice di conferma al nuovo indirizzo. L'email attuale resta attiva finché non lo confermi.
          </p>
          {messaggioAvviso}
          <Pulsante type="submit" larghezzaPiena disabled={richiesta.bloccato}>
            <TestoPulsante
              testo="Invia codice di conferma"
              inviando={richiesta.inviando}
              secondiAttesa={richiesta.secondiAttesa}
            />
          </Pulsante>
        </form>
      ) : (
        <form noValidate onSubmit={invioCodice}>
          <p>
            Abbiamo inviato un codice a 6 cifre a <strong>{pulisciEmail(dati.nuovaEmail)}</strong>.
          </p>
          <CodiceSeiCifre
            id="codiceCambioEmail"
            etichetta="Codice di conferma"
            valore={codice}
            onChange={(valore) => {
              setCodice(valore)
              setErroreCodice(undefined)
            }}
            errore={erroreCodice}
            ref={campoCodice}
          />
          {messaggioAvviso}
          <div className={stili.pulsanti}>
            <Pulsante type="submit" variante="primario" disabled={conferma.bloccato}>
              <TestoPulsante testo="Conferma nuova email" inviando={conferma.inviando} secondiAttesa={0} />
            </Pulsante>
            <Pulsante variante="neutro" onClick={nuovoCodice} disabled={reinvio.bloccato}>
              <TestoPulsante
                testo="Invia di nuovo il codice"
                inviando={reinvio.inviando}
                secondiAttesa={reinvio.secondiAttesa}
              />
            </Pulsante>
            <button type="button" className={stili.mostra} onClick={altraEmail}>
              Usa un'altra email
            </button>
          </div>
        </form>
      )}
    </Scheda>
  )
}

export default SezioneCambioEmail
