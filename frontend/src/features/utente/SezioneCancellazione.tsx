import { useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import CampoTesto from '../../common/CampoTesto'
import { messaggioErrore } from '../../common/messaggiErrore'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import { MESSAGGIO_ACCOUNT_ELIMINATO, type StatoVersoLogin } from '../../common/statoVersoLogin'
import TestoPulsante from '../../common/TestoPulsante'
import { useInvioForm } from '../../common/useInvioForm'
import { validaCancellazione } from '../../common/validazione'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import stili from './profilo.module.css'
import { cancellaAccount } from './utenteThunk'

/**
 * Zona pericolo: cancellazione dell'account (me.md, Cancellazione account) con un secondo passaggio esplicito
 * (niente window.confirm). Dopo il 204 si va a /logout e poi al login con "Account eliminato", senza email.
 * Il SUPERADMIN non può cancellarsi (il backend risponde 403): a lui la sezione spiega solo perché.
 */
function SezioneCancellazione() {
  const dispatch = useAppDispatch()
  const naviga = useNavigate()
  const ruolo = useAppSelector((stato) => stato.sessione.utente?.ruolo)
  const { invia, inviando, secondiAttesa, bloccato } = useInvioForm()
  const [aperto, setAperto] = useState(false)
  const [password, setPassword] = useState('')
  const [errorePassword, setErrorePassword] = useState<string | undefined>()
  const [erroreGenerale, setErroreGenerale] = useState<string | null>(null)
  const campoPassword = useRef<HTMLInputElement>(null)

  function chiudi() {
    setAperto(false)
    setPassword('')
    setErrorePassword(undefined)
    setErroreGenerale(null)
  }

  async function invio(evento: FormEvent) {
    evento.preventDefault()
    if (bloccato) {
      return
    }
    setErroreGenerale(null)
    const erroreLocale = validaCancellazione(password).password
    if (erroreLocale) {
      setErrorePassword(erroreLocale)
      campoPassword.current?.focus()
      return
    }
    const errore = await invia(() => dispatch(cancellaAccount({ password })).unwrap())
    if (!errore) {
      const stato: StatoVersoLogin = { messaggio: MESSAGGIO_ACCOUNT_ELIMINATO, email: '' }
      naviga('/logout', { replace: true, state: stato })
    } else if (errore.codice === 'PASSWORD_ATTUALE_ERRATA' || errore.campi.password) {
      setErrorePassword(errore.campi.password ?? messaggioErrore(errore.codice))
      campoPassword.current?.focus()
    } else {
      setErroreGenerale(messaggioErrore(errore.codice))
    }
  }

  return (
    <section className={stili.zonaPericolo} aria-labelledby="titolo-pericolo">
      <h2 id="titolo-pericolo" className={stili.titoloPericolo}>
        Zona pericolo
      </h2>
      {ruolo === 'SUPERADMIN' ? (
        <p className="mb-0">Il superadmin non può eliminare il proprio account.</p>
      ) : aperto ? (
        <form noValidate onSubmit={invio}>
          <p>
            Questa azione è irreversibile: il tuo profilo, la tua email e la tua foto verranno eliminati. I tuoi
            progressi resteranno solo in forma anonima.
          </p>
          <CampoTesto
            id="passwordCancellazione"
            etichetta="Password"
            tipo="password"
            valore={password}
            onChange={(valore) => {
              setPassword(valore)
              setErrorePassword(undefined)
            }}
            autoComplete="current-password"
            errore={errorePassword}
            ref={campoPassword}
          />
          <MessaggioErrore testo={erroreGenerale} />
          <div className={stili.pulsanti}>
            <Pulsante type="submit" variante="pericolo" disabled={bloccato}>
              <TestoPulsante testo="Elimina definitivamente" inviando={inviando} secondiAttesa={secondiAttesa} />
            </Pulsante>
            <Pulsante variante="neutro" onClick={chiudi} disabled={inviando}>
              Annulla
            </Pulsante>
          </div>
        </form>
      ) : (
        <>
          <p>Elimina il tuo account QuackFit.</p>
          <Pulsante variante="pericolo" larghezzaPiena onClick={() => setAperto(true)}>
            Elimina account
          </Pulsante>
        </>
      )}
    </section>
  )
}

export default SezioneCancellazione
