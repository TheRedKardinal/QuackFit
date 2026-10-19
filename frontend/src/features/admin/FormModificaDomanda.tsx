import { useEffect, useRef, useState, type FormEvent } from 'react'
import CampoTesto from '../../common/CampoTesto'
import { messaggioErrore } from '../../common/messaggiErrore'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import TestoPulsante from '../../common/TestoPulsante'
import { useInvioForm } from '../../common/useInvioForm'
import {
  MASSIMO_SPIEGAZIONE,
  MASSIMO_TESTO_DOMANDA,
  strip,
  validaModificaDomanda,
} from '../../common/validazione'
import type { DomandaAdmin, ModificaDomandaRequest } from './tipi'
import { useErroriForm } from './useErroriForm'
import stili from './admin.module.css'

/** Un testo modificabile di opzione o elemento, con la chiave d'errore del backend e l'etichetta del campo. */
interface Voce {
  id: string
  chiave: string
  etichetta: string
}

/**
 * Le voci modificabili nell'ordine della richiesta, da cui dipendono le chiavi d'errore del backend:
 * scelta multipla → `opzioni[i].testo`; abbinamento → `elementi[k].testo` (sinistro e destro di ogni coppia).
 * Vero o falso: nessuna (i testi "Vero" e "Falso" sono fissi).
 */
function voci(domanda: DomandaAdmin): Voce[] {
  if (domanda.tipo === 'SCELTA_MULTIPLA') {
    return domanda.opzioni.map((opzione, i) => ({ id: opzione.id, chiave: `opzioni[${i}].testo`, etichetta: `Opzione ${i + 1}` }))
  }
  if (domanda.tipo === 'ABBINAMENTO') {
    return domanda.coppie.flatMap((coppia, i) => [
      { id: coppia.sinistro.id, chiave: `elementi[${2 * i}].testo`, etichetta: `Sinistra ${i + 1}` },
      { id: coppia.destro.id, chiave: `elementi[${2 * i + 1}].testo`, etichetta: `Destra ${i + 1}` },
    ])
  }
  return []
}

function testiIniziali(domanda: DomandaAdmin): Record<string, string> {
  const testi: Record<string, string> = {}
  for (const opzione of domanda.opzioni) {
    testi[opzione.id] = opzione.testo
  }
  for (const coppia of domanda.coppie) {
    testi[coppia.sinistro.id] = coppia.sinistro.testo
    testi[coppia.destro.id] = coppia.destro.testo
  }
  return testi
}

interface Props {
  domanda: DomandaAdmin
  /** Salva i testi; se fallisce rifiuta con l'errore del backend (come unwrap() di un thunk). */
  onSalva: (corpo: ModificaDomandaRequest) => Promise<unknown>
  onAnnulla: () => void
}

/**
 * Modifica dei soli testi di una domanda (admin.md, Domande): testo, spiegazione e testi di opzioni o elementi.
 * Tipo, risposta corretta, numero di opzioni e collegamenti non si cambiano: lo spiega la nota nel form.
 * Si inviano sempre tutti i testi; gli errori del backend (anche i testi ripetuti) vanno sotto il campo giusto.
 */
function FormModificaDomanda({ domanda, onSalva, onAnnulla }: Props) {
  const { invia, inviando, secondiAttesa, bloccato } = useInvioForm()
  const { errori, registra, togli, mostra } = useErroriForm()
  const [testo, setTesto] = useState(domanda.testo)
  const [spiegazione, setSpiegazione] = useState(domanda.spiegazione)
  const [testi, setTesti] = useState(() => testiIniziali(domanda))
  const [erroreGenerale, setErroreGenerale] = useState<string | null>(null)
  const primoCampo = useRef<HTMLInputElement | null>(null)
  const elenco = voci(domanda)
  const prefisso = `domanda-${domanda.id}`

  useEffect(() => {
    primoCampo.current?.focus()
  }, [])

  function corpo(): ModificaDomandaRequest {
    const base = { testo, spiegazione }
    const nuovi = elenco.map((voce) => ({ id: voce.id, testo: testi[voce.id] }))
    if (domanda.tipo === 'SCELTA_MULTIPLA') {
      return { ...base, opzioni: nuovi }
    }
    if (domanda.tipo === 'ABBINAMENTO') {
      return { ...base, elementi: nuovi }
    }
    return base
  }

  async function invio(evento: FormEvent) {
    evento.preventDefault()
    if (bloccato) {
      return
    }
    setErroreGenerale(null)
    const erroriLocali = validaModificaDomanda({
      testo,
      spiegazione,
      testi: elenco.map((voce) => ({ chiave: voce.chiave, testo: testi[voce.id] })),
    })
    if (Object.keys(erroriLocali).length > 0) {
      mostra(erroriLocali)
      return
    }
    const errore = await invia(() => onSalva(corpo()))
    if (!errore) {
      return
    }
    if (errore.codice === 'VALIDAZIONE' && Object.keys(errore.campi).length > 0) {
      mostra(errore.campi)
    } else {
      setErroreGenerale(messaggioErrore(errore.codice))
    }
  }

  return (
    <form noValidate onSubmit={invio} className={stili.formDomanda} aria-label={`Modifica la domanda ${domanda.ordine}`}>
      <p className={stili.intestazioneForm}>Modifica la domanda {domanda.ordine}</p>
      <p className={stili.nota}>
        Si cambiano solo i testi. Per cambiare tipo, risposta corretta o numero di opzioni: elimina la domanda e creane una
        nuova.
      </p>
      <CampoTesto
        id={`${prefisso}-testo`}
        etichetta="Testo della domanda"
        valore={testo}
        onChange={(valore) => {
          setTesto(valore)
          togli('testo')
        }}
        errore={errori.testo}
        accessorio={`${strip(testo).length}/${MASSIMO_TESTO_DOMANDA}`}
        ref={(elemento) => {
          primoCampo.current = elemento
          registra('testo')(elemento)
        }}
      />
      <CampoTesto
        id={`${prefisso}-spiegazione`}
        etichetta="Spiegazione (mostrata dopo la risposta)"
        valore={spiegazione}
        onChange={(valore) => {
          setSpiegazione(valore)
          togli('spiegazione')
        }}
        errore={errori.spiegazione}
        accessorio={`${strip(spiegazione).length}/${MASSIMO_SPIEGAZIONE}`}
        ref={registra('spiegazione')}
      />
      {domanda.tipo === 'VERO_FALSO' && (
        <p className={stili.nota}>
          Le opzioni "Vero" e "Falso" sono fisse. Risposta giusta:{' '}
          <strong>{domanda.opzioni.find((opzione) => opzione.corretta)?.testo}</strong>.
        </p>
      )}
      {(errori.opzioni || errori.elementi) && <p className={stili.erroreGruppo}>{errori.opzioni ?? errori.elementi}</p>}
      {elenco.map((voce) => (
        <CampoTesto
          key={voce.id}
          id={`${prefisso}-${voce.id}`}
          etichetta={voce.etichetta}
          valore={testi[voce.id]}
          onChange={(valore) => {
            setTesti((precedenti) => ({ ...precedenti, [voce.id]: valore }))
            togli(voce.chiave, voce.chiave.replace('.testo', '.id'))
          }}
          errore={errori[voce.chiave] ?? errori[voce.chiave.replace('.testo', '.id')]}
          ref={registra(voce.chiave)}
        />
      ))}
      <MessaggioErrore testo={erroreGenerale} />
      <div className={stili.azioniConferma}>
        <Pulsante type="submit" disabled={bloccato}>
          <TestoPulsante testo="Salva testi" inviando={inviando} secondiAttesa={secondiAttesa} />
        </Pulsante>
        <Pulsante variante="neutro" onClick={onAnnulla} disabled={inviando}>
          Annulla
        </Pulsante>
      </div>
    </form>
  )
}

export default FormModificaDomanda
