import { useRef, useState, type FormEvent } from 'react'
import CampoTesto from '../../common/CampoTesto'
import { messaggioErrore } from '../../common/messaggiErrore'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import TestoPulsante from '../../common/TestoPulsante'
import { useInvioForm } from '../../common/useInvioForm'
import { MASSIMO_NOME, strip, validaDatiProfilo, type DatiProfilo, type ErroriCampi } from '../../common/validazione'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import stili from './profilo.module.css'
import { modificaDati } from './utenteThunk'

type Campo = keyof DatiProfilo
const ORDINE_CAMPI: Campo[] = ['nome', 'cognome']

/** Modifica di nome e cognome (me.md, Modifica dati): stesse regole della registrazione, nessun logout. */
function SezioneDatiPersonali() {
  const dispatch = useAppDispatch()
  const utente = useAppSelector((stato) => stato.sessione.utente)
  const { invia, inviando, secondiAttesa, bloccato } = useInvioForm()
  const [dati, setDati] = useState<DatiProfilo>({ nome: utente?.nome ?? '', cognome: utente?.cognome ?? '' })
  const [errori, setErrori] = useState<ErroriCampi>({})
  const [erroreGenerale, setErroreGenerale] = useState<string | null>(null)
  const [salvati, setSalvati] = useState(false)
  const campi = useRef<Partial<Record<Campo, HTMLInputElement | null>>>({})

  function cambia(campo: Campo, valore: string) {
    setDati((precedenti) => ({ ...precedenti, [campo]: valore }))
    setErrori((precedenti) => {
      const altri = { ...precedenti }
      delete altri[campo]
      return altri
    })
    setSalvati(false)
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
    setSalvati(false)
    const erroriLocali = validaDatiProfilo(dati)
    if (Object.keys(erroriLocali).length > 0) {
      mostraErrori(erroriLocali)
      return
    }
    const errore = await invia(() => dispatch(modificaDati(dati)).unwrap())
    if (!errore) {
      setSalvati(true)
    } else if (errore.codice === 'VALIDAZIONE' && Object.keys(errore.campi).length > 0) {
      mostraErrori(errore.campi)
    } else {
      setErroreGenerale(messaggioErrore(errore.codice))
    }
  }

  return (
    <Scheda>
      <h2 className={stili.titoloSezione}>Dati personali</h2>
      <form noValidate onSubmit={invio}>
        <div className={stili.riga}>
          <CampoTesto
            id="nome"
            etichetta="Nome"
            valore={dati.nome}
            onChange={(valore) => cambia('nome', valore)}
            autoComplete="given-name"
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
            errore={errori.cognome}
            accessorio={`${strip(dati.cognome).length}/${MASSIMO_NOME}`}
            ref={(elemento) => {
              campi.current.cognome = elemento
            }}
          />
        </div>

        {salvati && (
          <p role="status" className={stili.messaggioOk}>
            ✓ Dati salvati.
          </p>
        )}
        <MessaggioErrore testo={erroreGenerale} />

        <Pulsante type="submit" larghezzaPiena disabled={bloccato}>
          <TestoPulsante testo="Salva dati" inviando={inviando} secondiAttesa={secondiAttesa} />
        </Pulsante>
      </form>
    </Scheda>
  )
}

export default SezioneDatiPersonali
