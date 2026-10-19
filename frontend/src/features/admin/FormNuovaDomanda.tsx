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
  validaNuovaDomanda,
  type DatiNuovaDomanda,
} from '../../common/validazione'
import { NOMI_TIPO } from './nomiTipo'
import type { CreazioneDomandaRequest, TipoDomanda } from './tipi'
import { useErroriForm } from './useErroriForm'
import stili from './admin.module.css'

/** Opzioni e coppie: da 2 a 6 (admin.md, Domande); il form non permette di uscire da questi limiti. */
const MINIMO = 2
const MASSIMO = 6

const TIPI: TipoDomanda[] = ['VERO_FALSO', 'SCELTA_MULTIPLA', 'ABBINAMENTO']

const VUOTA: DatiNuovaDomanda = {
  tipo: 'VERO_FALSO',
  testo: '',
  spiegazione: '',
  rispostaVero: null,
  opzioni: ['', ''],
  corretta: null,
  coppie: [
    { sinistra: '', destra: '' },
    { sinistra: '', destra: '' },
  ],
}

/** Il body della POST: solo i campi del tipo scelto (gli altri devono essere assenti). */
function corpo(dati: DatiNuovaDomanda): CreazioneDomandaRequest {
  const comune = { testo: dati.testo, spiegazione: dati.spiegazione, tipo: dati.tipo }
  switch (dati.tipo) {
    case 'VERO_FALSO':
      return { ...comune, rispostaVero: dati.rispostaVero === true }
    case 'SCELTA_MULTIPLA':
      return { ...comune, opzioni: dati.opzioni.map((testo, i) => ({ testo, corretta: i === dati.corretta })) }
    case 'ABBINAMENTO':
      return { ...comune, coppie: dati.coppie }
  }
}

interface Props {
  /** Salva la domanda; se fallisce rifiuta con l'errore del backend (come unwrap() di un thunk). */
  onSalva: (corpo: CreazioneDomandaRequest) => Promise<unknown>
  onAnnulla: () => void
}

/**
 * Nuova domanda (admin.md, Domande): tipo con tre radio e i campi di quel tipo.
 * - Vero o falso: la risposta giusta con due radio
 * - Scelta multipla: da 2 a 6 opzioni, la corretta con un radio (quindi una sola)
 * - Abbinamento: da 2 a 6 coppie sinistra/destra
 * Nel frontend solo obbligatori e lunghezze; i testi ripetuti li segnala il backend, sotto il campo giusto.
 */
function FormNuovaDomanda({ onSalva, onAnnulla }: Props) {
  const { invia, inviando, secondiAttesa, bloccato } = useInvioForm()
  const { errori, registra, togli, mostra } = useErroriForm()
  const [dati, setDati] = useState<DatiNuovaDomanda>(VUOTA)
  const [erroreGenerale, setErroreGenerale] = useState<string | null>(null)
  const primoCampo = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    primoCampo.current?.focus()
  }, [])

  function cambia(modifica: Partial<DatiNuovaDomanda>, ...chiavi: string[]) {
    setDati((precedenti) => ({ ...precedenti, ...modifica }))
    togli(...chiavi)
  }

  /** Aggiungere o togliere righe sposta gli indici: gli errori delle righe non valgono più. */
  function togliErroriRighe() {
    const chiavi = Object.keys(errori).filter((chiave) => chiave.startsWith('opzioni') || chiave.startsWith('coppie'))
    togli(...chiavi)
  }

  function cambiaOpzione(indice: number, testo: string) {
    cambia({ opzioni: dati.opzioni.map((valore, i) => (i === indice ? testo : valore)) }, `opzioni[${indice}].testo`)
  }

  function togliOpzione(indice: number) {
    const corretta =
      dati.corretta === null || dati.corretta === indice ? null : dati.corretta > indice ? dati.corretta - 1 : dati.corretta
    setDati((precedenti) => ({ ...precedenti, opzioni: precedenti.opzioni.filter((_, i) => i !== indice), corretta }))
    togliErroriRighe()
  }

  function cambiaCoppia(indice: number, lato: 'sinistra' | 'destra', testo: string) {
    cambia(
      { coppie: dati.coppie.map((coppia, i) => (i === indice ? { ...coppia, [lato]: testo } : coppia)) },
      `coppie[${indice}].${lato}`,
    )
  }

  async function invio(evento: FormEvent) {
    evento.preventDefault()
    if (bloccato) {
      return
    }
    setErroreGenerale(null)
    const erroriLocali = validaNuovaDomanda(dati)
    if (Object.keys(erroriLocali).length > 0) {
      mostra(erroriLocali)
      return
    }
    const errore = await invia(() => onSalva(corpo(dati)))
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
    <form noValidate onSubmit={invio} className={stili.formDomanda} aria-label="Nuova domanda">
      <p className={stili.intestazioneForm}>Nuova domanda</p>

      <fieldset className={stili.gruppo}>
        <legend className={stili.legenda}>Tipo</legend>
        <div className={stili.sceltaTipo}>
          {TIPI.map((tipo) => (
            <label key={tipo} className={stili.radio}>
              <input
                type="radio"
                name="nuova-domanda-tipo"
                value={tipo}
                checked={dati.tipo === tipo}
                onChange={() => {
                  setDati((precedenti) => ({ ...precedenti, tipo }))
                  togli('rispostaVero', 'opzioni', 'coppie')
                  togliErroriRighe()
                }}
              />
              {NOMI_TIPO[tipo]}
            </label>
          ))}
        </div>
      </fieldset>

      <CampoTesto
        id="nuova-domanda-testo"
        etichetta="Testo della domanda"
        valore={dati.testo}
        onChange={(testo) => cambia({ testo }, 'testo')}
        errore={errori.testo}
        accessorio={`${strip(dati.testo).length}/${MASSIMO_TESTO_DOMANDA}`}
        ref={(elemento) => {
          primoCampo.current = elemento
          registra('testo')(elemento)
        }}
      />
      <CampoTesto
        id="nuova-domanda-spiegazione"
        etichetta="Spiegazione (mostrata dopo la risposta)"
        valore={dati.spiegazione}
        onChange={(spiegazione) => cambia({ spiegazione }, 'spiegazione')}
        errore={errori.spiegazione}
        accessorio={`${strip(dati.spiegazione).length}/${MASSIMO_SPIEGAZIONE}`}
        ref={registra('spiegazione')}
      />

      {dati.tipo === 'VERO_FALSO' && (
        <fieldset className={stili.gruppo} aria-describedby={errori.rispostaVero ? 'nuova-domanda-vf-errore' : undefined}>
          <legend className={stili.legenda}>La risposta giusta è</legend>
          <div className={stili.sceltaTipo}>
            {[true, false].map((vero) => (
              <label key={String(vero)} className={stili.radio}>
                <input
                  type="radio"
                  name="nuova-domanda-vf"
                  checked={dati.rispostaVero === vero}
                  onChange={() => cambia({ rispostaVero: vero }, 'rispostaVero')}
                />
                {vero ? 'Vero' : 'Falso'}
              </label>
            ))}
          </div>
          {errori.rispostaVero && (
            <p id="nuova-domanda-vf-errore" className={stili.erroreGruppo}>
              {errori.rispostaVero}
            </p>
          )}
        </fieldset>
      )}

      {dati.tipo === 'SCELTA_MULTIPLA' && (
        <fieldset className={stili.gruppo} aria-describedby={errori.opzioni ? 'nuova-domanda-opzioni-errore' : undefined}>
          <legend className={stili.legenda}>Opzioni (da {MINIMO} a {MASSIMO}): scegli quella corretta</legend>
          {dati.opzioni.map((testo, i) => (
            <div key={i} className={stili.riga}>
              <CampoTesto
                id={`nuova-domanda-opzione-${i}`}
                etichetta={`Opzione ${i + 1}`}
                valore={testo}
                onChange={(valore) => cambiaOpzione(i, valore)}
                errore={errori[`opzioni[${i}].testo`]}
                ref={registra(`opzioni[${i}].testo`)}
              />
              <label className={stili.radio}>
                <input
                  type="radio"
                  name="nuova-domanda-corretta"
                  aria-label={`L'opzione ${i + 1} è la corretta`}
                  checked={dati.corretta === i}
                  onChange={() => cambia({ corretta: i }, 'opzioni')}
                />
                Corretta
              </label>
              <Pulsante
                variante="neutro"
                aria-label={`Togli l'opzione ${i + 1}`}
                disabled={dati.opzioni.length <= MINIMO}
                onClick={() => togliOpzione(i)}
              >
                Togli
              </Pulsante>
            </div>
          ))}
          {errori.opzioni && (
            <p id="nuova-domanda-opzioni-errore" className={stili.erroreGruppo}>
              {errori.opzioni}
            </p>
          )}
          <Pulsante
            variante="neutro"
            disabled={dati.opzioni.length >= MASSIMO}
            onClick={() => {
              setDati((precedenti) => ({ ...precedenti, opzioni: [...precedenti.opzioni, ''] }))
              togli('opzioni')
            }}
          >
            + Aggiungi opzione
          </Pulsante>
        </fieldset>
      )}

      {dati.tipo === 'ABBINAMENTO' && (
        <fieldset className={stili.gruppo} aria-describedby={errori.coppie ? 'nuova-domanda-coppie-errore' : undefined}>
          <legend className={stili.legenda}>Coppie giuste (da {MINIMO} a {MASSIMO})</legend>
          {dati.coppie.map((coppia, i) => (
            <div key={i} className={stili.riga}>
              <CampoTesto
                id={`nuova-domanda-sinistra-${i}`}
                etichetta={`Sinistra ${i + 1}`}
                valore={coppia.sinistra}
                onChange={(valore) => cambiaCoppia(i, 'sinistra', valore)}
                errore={errori[`coppie[${i}].sinistra`]}
                ref={registra(`coppie[${i}].sinistra`)}
              />
              <CampoTesto
                id={`nuova-domanda-destra-${i}`}
                etichetta={`Destra ${i + 1}`}
                valore={coppia.destra}
                onChange={(valore) => cambiaCoppia(i, 'destra', valore)}
                errore={errori[`coppie[${i}].destra`]}
                ref={registra(`coppie[${i}].destra`)}
              />
              <Pulsante
                variante="neutro"
                aria-label={`Togli la coppia ${i + 1}`}
                disabled={dati.coppie.length <= MINIMO}
                onClick={() => {
                  setDati((precedenti) => ({ ...precedenti, coppie: precedenti.coppie.filter((_, j) => j !== i) }))
                  togliErroriRighe()
                }}
              >
                Togli
              </Pulsante>
            </div>
          ))}
          {errori.coppie && (
            <p id="nuova-domanda-coppie-errore" className={stili.erroreGruppo}>
              {errori.coppie}
            </p>
          )}
          <Pulsante
            variante="neutro"
            disabled={dati.coppie.length >= MASSIMO}
            onClick={() => {
              setDati((precedenti) => ({ ...precedenti, coppie: [...precedenti.coppie, { sinistra: '', destra: '' }] }))
              togli('coppie')
            }}
          >
            + Aggiungi coppia
          </Pulsante>
        </fieldset>
      )}

      <MessaggioErrore testo={erroreGenerale} />
      <div className={stili.azioniConferma}>
        <Pulsante type="submit" disabled={bloccato}>
          <TestoPulsante testo="Crea domanda" inviando={inviando} secondiAttesa={secondiAttesa} />
        </Pulsante>
        <Pulsante variante="neutro" onClick={onAnnulla} disabled={inviando}>
          Annulla
        </Pulsante>
      </div>
    </form>
  )
}

export default FormNuovaDomanda
