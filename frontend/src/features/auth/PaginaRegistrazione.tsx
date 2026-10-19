import { useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { DatiErrore } from '../../api/ApiError'
import CampoTesto from '../../common/CampoTesto'
import CoachQuack from '../../common/CoachQuack'
import { messaggioErrore } from '../../common/messaggiErrore'
import {
  MASSIMO_NOME,
  strip,
  validaRegistrazione,
  type DatiRegistrazione,
  type ErroriCampi,
} from '../../common/validazione'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import { useAppDispatch } from '../../store/hooks'
import stili from './accesso.module.css'
import { registra } from './authThunk'
import BarraAccesso from './BarraAccesso'
import RiquadroRequisiti from '../../common/RiquadroRequisiti'
import TestoPulsante from '../../common/TestoPulsante'
import { useInvioForm } from '../../common/useInvioForm'

type Campo = keyof DatiRegistrazione
const ORDINE_CAMPI: Campo[] = ['nome', 'cognome', 'email', 'password']

const DATI_VUOTI: DatiRegistrazione = { nome: '', cognome: '', email: '', password: '' }

/**
 * Registrazione (auth.md, Registrazione). Validazione nel frontend con le regole di common/validazione;
 * gli errori `campi` del backend hanno comunque la precedenza e si mostrano sotto i campi.
 */
function PaginaRegistrazione() {
  const dispatch = useAppDispatch()
  const { invia, inviando, secondiAttesa, bloccato } = useInvioForm()
  const [dati, setDati] = useState<DatiRegistrazione>(DATI_VUOTI)
  const [errori, setErrori] = useState<ErroriCampi>({})
  const [emailGiaUsata, setEmailGiaUsata] = useState(false)
  const [erroreGenerale, setErroreGenerale] = useState<string | null>(null)
  const [mostraPassword, setMostraPassword] = useState(false)
  const campi = useRef<Partial<Record<Campo, HTMLInputElement | null>>>({})


  function cambia(campo: Campo, valore: string) {
    setDati((precedenti) => ({ ...precedenti, [campo]: valore }))
    // l'errore del campo sparisce appena lo si corregge
    setErrori((precedenti) => {
      const altri = { ...precedenti }
      delete altri[campo]
      return altri
    })
    if (campo === 'email') {
      setEmailGiaUsata(false)
    }
  }

  /** Mostra gli errori e porta il cursore sul primo campo sbagliato. */
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
    } else if (errore.codice === 'EMAIL_GIA_USATA') {
      setEmailGiaUsata(true)
      mostraErrori({ email: messaggioErrore(errore.codice) })
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
    const erroriLocali = validaRegistrazione(dati)
    if (Object.keys(erroriLocali).length > 0) {
      setEmailGiaUsata(false)
      mostraErrori(erroriLocali)
      return
    }
    const errore = await invia(() => dispatch(registra(dati)).unwrap())
    if (errore) {
      gestisciErrore(errore)
    }
  }

  return (
    <main className={stili.pagina}>
      <BarraAccesso titolo="Registrazione" />

      <Scheda className={stili.coach}>
        <CoachQuack dimensione={72} />
        <div>
          <p className={stili.nomeCoach}>Coach Quack</p>
          <p className={stili.fraseCoach}>“Bastano pochi minuti al giorno per imparare le basi del fitness.”</p>
        </div>
      </Scheda>

      <h2 className={stili.titolo}>Crea il tuo profilo</h2>
      <p className={stili.sottotitolo}>Impara la teoria del fitness, una lezione breve alla volta.</p>

      <form noValidate onSubmit={invio}>
        <div className={stili.riga}>
          <CampoTesto
            id="nome"
            etichetta="Nome"
            valore={dati.nome}
            onChange={(valore) => cambia('nome', valore)}
            autoComplete="given-name"
            placeholder="Mario"
            errore={errori.nome}
            accessorio={`${strip(dati.nome).length}/${MASSIMO_NOME}`}
            ref={(elemento) => {
              campi.current.nome = elemento
            }}
          />
          <CampoTesto
            id="cognome"
            etichetta="Cognome"
            valore={dati.cognome}
            onChange={(valore) => cambia('cognome', valore)}
            autoComplete="family-name"
            placeholder="Rossi"
            errore={errori.cognome}
            accessorio={`${strip(dati.cognome).length}/${MASSIMO_NOME}`}
            ref={(elemento) => {
              campi.current.cognome = elemento
            }}
          />
        </div>

        <CampoTesto
          id="email"
          etichetta="Email"
          tipo="email"
          valore={dati.email}
          onChange={(valore) => cambia('email', valore)}
          autoComplete="email"
          placeholder="mario.rossi@example.com"
          errore={errori.email}
          ref={(elemento) => {
            campi.current.email = elemento
          }}
        >
          {emailGiaUsata && (
            <p className="mt-1 mb-0">
              <Link to="/forgot-password" className={stili.link}>
                Password dimenticata?
              </Link>{' '}
              oppure <Link to="/login" className={stili.link}>accedi</Link>
            </p>
          )}
        </CampoTesto>

        <CampoTesto
          id="password"
          etichetta="Password"
          tipo={mostraPassword ? 'text' : 'password'}
          valore={dati.password}
          onChange={(valore) => cambia('password', valore)}
          autoComplete="new-password"
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

        <RiquadroRequisiti password={dati.password} />

        <div className={stili.riquadro}>
          <p className={stili.nota}>Registrandoti riceverai un codice a 6 cifre via email per verificare il tuo indirizzo.</p>
        </div>

        <MessaggioErrore testo={erroreGenerale} />

        <div className={stili.pulsantiera}>
          <Pulsante type="submit" disabled={bloccato}>
            <TestoPulsante testo="Crea account" inviando={inviando} secondiAttesa={secondiAttesa} />
          </Pulsante>
        </div>
      </form>

      <p className={`${stili.centrato} mt-4`}>
        Hai già un account?{' '}
        <Link to="/login" className={stili.link}>
          Accedi
        </Link>
      </p>
    </main>
  )
}

export default PaginaRegistrazione
