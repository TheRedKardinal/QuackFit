import { useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import type { DatiErrore } from '../../api/ApiError'
import CampoTesto from '../../common/CampoTesto'
import { messaggioErrore } from '../../common/messaggiErrore'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import RiquadroRequisiti from '../../common/RiquadroRequisiti'
import Scheda from '../../common/Scheda'
import { MESSAGGIO_PASSWORD_AGGIORNATA, type StatoVersoLogin } from '../../common/statoVersoLogin'
import TestoPulsante from '../../common/TestoPulsante'
import { useInvioForm } from '../../common/useInvioForm'
import { validaCambioPassword, type DatiCambioPassword, type ErroriCampi } from '../../common/validazione'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import stili from './profilo.module.css'
import { cambiaPassword } from './utenteThunk'

type Campo = keyof DatiCambioPassword
const ORDINE_CAMPI: Campo[] = ['passwordAttuale', 'nuovaPassword']

/**
 * Cambio password (me.md, Cambio password). Il backend risponde 204 senza un nuovo token e fa logout ovunque:
 * il token di questa sessione non vale più. Quindi, appena va bene, si va alla rotta /logout (common/Uscita) con il
 * messaggio e l'email (mai la password): lì si fa il logout e poi si arriva al login, senza che la guardia di questa
 * pagina porti al login per conto suo perdendo il messaggio.
 */
function SezioneCambioPassword() {
  const dispatch = useAppDispatch()
  const naviga = useNavigate()
  const email = useAppSelector((stato) => stato.sessione.utente?.email ?? '')
  const { invia, inviando, secondiAttesa, bloccato } = useInvioForm()
  const [dati, setDati] = useState<DatiCambioPassword>({ passwordAttuale: '', nuovaPassword: '' })
  const [errori, setErrori] = useState<ErroriCampi>({})
  const [erroreGenerale, setErroreGenerale] = useState<string | null>(null)
  const [mostraPassword, setMostraPassword] = useState(false)
  const campi = useRef<Partial<Record<Campo, HTMLInputElement | null>>>({})

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
    } else if (errore.codice === 'PASSWORD_ATTUALE_ERRATA') {
      mostraErrori({ passwordAttuale: messaggioErrore(errore.codice) })
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
    const erroriLocali = validaCambioPassword(dati)
    if (Object.keys(erroriLocali).length > 0) {
      mostraErrori(erroriLocali)
      return
    }
    const errore = await invia(() => dispatch(cambiaPassword(dati)).unwrap())
    if (errore) {
      gestisciErrore(errore)
      return
    }
    const stato: StatoVersoLogin = { messaggio: MESSAGGIO_PASSWORD_AGGIORNATA, email }
    naviga('/logout', { replace: true, state: stato })
  }

  const tipo = mostraPassword ? 'text' : 'password'
  const pulsanteMostra = (
    <button type="button" className={stili.mostra} onClick={() => setMostraPassword((m) => !m)}>
      {mostraPassword ? 'Nascondi' : 'Mostra'}
    </button>
  )

  return (
    <Scheda>
      <h2 className={stili.titoloSezione}>Sicurezza e password</h2>
      <form noValidate onSubmit={invio}>
        <CampoTesto
          id="passwordAttuale"
          etichetta="Password attuale"
          tipo={tipo}
          valore={dati.passwordAttuale}
          onChange={(valore) => cambia('passwordAttuale', valore)}
          autoComplete="current-password"
          errore={errori.passwordAttuale}
          accessorio={pulsanteMostra}
          ref={(elemento) => {
            campi.current.passwordAttuale = elemento
          }}
        />
        <CampoTesto
          id="nuovaPassword"
          etichetta="Nuova password"
          tipo={tipo}
          valore={dati.nuovaPassword}
          onChange={(valore) => cambia('nuovaPassword', valore)}
          autoComplete="new-password"
          errore={errori.nuovaPassword}
          ref={(elemento) => {
            campi.current.nuovaPassword = elemento
          }}
        />

        <RiquadroRequisiti password={dati.nuovaPassword} />
        <p className={stili.nota}>
          Cambiando la password uscirai da tutti i dispositivi, compreso questo: dovrai accedere di nuovo.
        </p>
        <MessaggioErrore testo={erroreGenerale} />

        <Pulsante type="submit" variante="successo" larghezzaPiena disabled={bloccato}>
          <TestoPulsante testo="Aggiorna password" inviando={inviando} secondiAttesa={secondiAttesa} />
        </Pulsante>
      </form>
    </Scheda>
  )
}

export default SezioneCambioPassword
