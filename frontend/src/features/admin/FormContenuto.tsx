import { useEffect, useRef, useState, type FormEvent } from 'react'
import CampoTesto from '../../common/CampoTesto'
import { messaggioErrore } from '../../common/messaggiErrore'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import TestoPulsante from '../../common/TestoPulsante'
import { useInvioForm } from '../../common/useInvioForm'
import {
  MASSIMO_DESCRIZIONE,
  MASSIMO_TITOLO,
  strip,
  validaContenuto,
  type DatiContenuto,
  type ErroriCampi,
} from '../../common/validazione'
import stili from './admin.module.css'

type Campo = keyof DatiContenuto
const ORDINE_CAMPI: Campo[] = ['titolo', 'descrizione']

interface Props {
  /** Prefisso degli id dei campi, unico nella pagina (es. "nuova-tappa", "lezione-<id>"). */
  id: string
  /** Titolo del form, letto anche dallo screen reader. */
  intestazione: string
  iniziale?: DatiContenuto
  testoInvio: string
  /** Salva i dati già validati; se fallisce rifiuta con l'errore del backend (come unwrap() di un thunk). */
  onSalva: (dati: DatiContenuto) => Promise<unknown>
  onAnnulla: () => void
}

/**
 * Form in linea di titolo e descrizione, per creare o modificare una tappa o una lezione (admin.md, Tappe e Lezioni).
 * Regole in common/validazione (validaContenuto); gli errori `campi` del backend vanno sotto i campi; con errori il
 * cursore va sul primo campo sbagliato. Una richiesta alla volta (useInvioForm).
 */
function FormContenuto({ id, intestazione, iniziale, testoInvio, onSalva, onAnnulla }: Props) {
  const { invia, inviando, secondiAttesa, bloccato } = useInvioForm()
  const [dati, setDati] = useState<DatiContenuto>(iniziale ?? { titolo: '', descrizione: '' })
  const [errori, setErrori] = useState<ErroriCampi>({})
  const [erroreGenerale, setErroreGenerale] = useState<string | null>(null)
  const campi = useRef<Partial<Record<Campo, HTMLInputElement | null>>>({})

  // All'apertura il cursore va sul titolo: si scrive subito, anche da tastiera
  useEffect(() => {
    campi.current.titolo?.focus()
  }, [])

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

  async function invio(evento: FormEvent) {
    evento.preventDefault()
    if (bloccato) {
      return
    }
    setErroreGenerale(null)
    const erroriLocali = validaContenuto(dati)
    if (Object.keys(erroriLocali).length > 0) {
      mostraErrori(erroriLocali)
      return
    }
    const errore = await invia(() => onSalva(dati))
    if (!errore) {
      return
    }
    if (errore.codice === 'VALIDAZIONE' && Object.keys(errore.campi).length > 0) {
      mostraErrori(errore.campi)
    } else {
      setErroreGenerale(messaggioErrore(errore.codice))
    }
  }

  return (
    <form noValidate onSubmit={invio} className={stili.formContenuto} aria-label={intestazione}>
      <p className={stili.intestazioneForm}>{intestazione}</p>
      <CampoTesto
        id={`${id}-titolo`}
        etichetta="Titolo"
        valore={dati.titolo}
        onChange={(valore) => cambia('titolo', valore)}
        errore={errori.titolo}
        accessorio={`${strip(dati.titolo).length}/${MASSIMO_TITOLO}`}
        ref={(elemento) => {
          campi.current.titolo = elemento
        }}
      />
      <CampoTesto
        id={`${id}-descrizione`}
        etichetta="Descrizione"
        valore={dati.descrizione}
        onChange={(valore) => cambia('descrizione', valore)}
        errore={errori.descrizione}
        accessorio={`${strip(dati.descrizione).length}/${MASSIMO_DESCRIZIONE}`}
        ref={(elemento) => {
          campi.current.descrizione = elemento
        }}
      />
      <MessaggioErrore testo={erroreGenerale} />
      <div className={stili.azioniConferma}>
        <Pulsante type="submit" disabled={bloccato}>
          <TestoPulsante testo={testoInvio} inviando={inviando} secondiAttesa={secondiAttesa} />
        </Pulsante>
        <Pulsante variante="neutro" onClick={onAnnulla} disabled={inviando}>
          Annulla
        </Pulsante>
      </div>
    </form>
  )
}

export default FormContenuto
