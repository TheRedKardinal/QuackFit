import { useRef, useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import CoachQuack from '../../common/CoachQuack'
import CodiceSeiCifre, { type CodiceSeiCifreHandle } from '../../common/CodiceSeiCifre'
import { messaggioErrore } from '../../common/messaggiErrore'
import { SECONDI_ATTESA_429 } from '../../common/useConteggioAllaRovescia'
import { validaCodice } from '../../common/validazione'
import Pulsante from '../../common/Pulsante'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import stili from './accesso.module.css'
import { reinviaCodice, verificaEmail } from './authThunk'
import BarraAccesso from './BarraAccesso'
import PulsanteEsci from '../../common/PulsanteEsci'
import TestoPulsante from '../../common/TestoPulsante'
import { useInvioForm } from '../../common/useInvioForm'

/**
 * Verifica dell'email con il codice a 6 cifre (auth.md, Verifica email e Nuovo codice).
 * Se il codice è giusto l'email risulta verificata e la pagina porta alla mappa.
 * "Invia nuovo codice" resta disattivato 60 secondi dopo un invio riuscito o un 429 ATTENDI_REINVIO.
 */
function PaginaVerificaEmail() {
  const dispatch = useAppDispatch()
  const utente = useAppSelector((stato) => stato.sessione.utente)
  const conferma = useInvioForm()
  const reinvio = useInvioForm()
  const [codice, setCodice] = useState('')
  const [erroreCodice, setErroreCodice] = useState<string | undefined>()
  const [avviso, setAvviso] = useState<{ tipo: 'ok' | 'errore'; testo: string } | null>(null)
  const campoCodice = useRef<CodiceSeiCifreHandle>(null)

  if (utente?.emailVerificata) {
    return <Navigate to="/" replace />
  }

  function cambiaCodice(valore: string) {
    setCodice(valore)
    setErroreCodice(undefined)
  }

  async function invio(evento: FormEvent) {
    evento.preventDefault()
    if (conferma.bloccato) {
      return
    }
    setAvviso(null)
    const errori = validaCodice(codice)
    if (errori.codice) {
      setErroreCodice(errori.codice)
      campoCodice.current?.focus()
      return
    }
    const errore = await conferma.invia(() => dispatch(verificaEmail({ codice })).unwrap())
    if (errore) {
      // CODICE_NON_VALIDO, CODICE_SCADUTO, TROPPI_TENTATIVI: si svuotano le caselle per riprovare
      setErroreCodice(errore.campi.codice ?? messaggioErrore(errore.codice))
      setCodice('')
      campoCodice.current?.focus(true)
    }
  }

  async function nuovoCodice() {
    setAvviso(null)
    const errore = await reinvio.invia(() => dispatch(reinviaCodice()).unwrap())
    if (errore) {
      setAvviso({ tipo: 'errore', testo: messaggioErrore(errore.codice) })
    } else {
      reinvio.avviaAttesa(SECONDI_ATTESA_429)
      // l'errore del codice precedente (es. scaduto) non vale più per quello nuovo
      setErroreCodice(undefined)
      setAvviso({ tipo: 'ok', testo: 'Ti abbiamo inviato un nuovo codice: controlla la posta.' })
    }
  }

  return (
    <main className={stili.pagina}>
      <BarraAccesso titolo="Verifica codice" />

      <div className={stili.centrato}>
        <CoachQuack className={stili.mascotte} />
        <h2 className={stili.titolo}>Verifica la tua email</h2>
        <p className={stili.sottotitolo}>
          Abbiamo inviato un codice numerico a 6 cifre a <strong>{utente?.email}</strong>. Inseriscilo per sbloccare le
          lezioni.
        </p>
      </div>

      <form noValidate onSubmit={invio}>
        <CodiceSeiCifre
          id="codice"
          etichetta="Codice di verifica"
          valore={codice}
          onChange={cambiaCodice}
          errore={erroreCodice}
          ref={campoCodice}
        />
        <div className={stili.pulsantiera}>
          <Pulsante type="submit" variante="successo" disabled={conferma.bloccato}>
            <TestoPulsante testo="Conferma codice" inviando={conferma.inviando} secondiAttesa={0} />
          </Pulsante>
        </div>
      </form>

      <div className={`${stili.pulsantiera} mt-3`}>
        {avviso && (
          <p
            role={avviso.tipo === 'errore' ? 'alert' : 'status'}
            className={avviso.tipo === 'ok' ? stili.messaggioOk : stili.erroreGenerale}
          >
            {avviso.testo}
          </p>
        )}
        <Pulsante variante="neutro" onClick={nuovoCodice} disabled={reinvio.bloccato}>
          <TestoPulsante testo="Invia nuovo codice" inviando={reinvio.inviando} secondiAttesa={reinvio.secondiAttesa} />
        </Pulsante>
        <p className={stili.notaPiccola}>Tra due invii serve un intervallo di 60 secondi.</p>
      </div>

      <section className={`${stili.riquadro} mt-4`} aria-label="Nota di sicurezza">
        <p className={stili.titoloRiquadro}>Nota di sicurezza</p>
        <p className={stili.nota}>
          Il codice scade dopo 15 minuti (massimo 5 tentativi). Trascorso questo tempo dovrai richiederne uno nuovo.
        </p>
      </section>

      <div className={stili.pulsantiera}>
        <PulsanteEsci />
      </div>
    </main>
  )
}

export default PaginaVerificaEmail
