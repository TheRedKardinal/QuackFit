import { useEffect, useImperativeHandle, useRef, type ChangeEvent, type KeyboardEvent, type Ref } from 'react'
import stili from './CodiceSeiCifre.module.css'

export const CIFRE_CODICE = 6

/**
 * Ciò che il form può fare sul campo: portarci il cursore sulla prima casella vuota, oppure sulla prima casella
 * (`dallInizio`) quando ha appena svuotato il codice e il nuovo valore non è ancora arrivato al campo.
 */
export interface CodiceSeiCifreHandle {
  focus: (dallInizio?: boolean) => void
}

interface Props {
  id: string
  etichetta: string
  /** Le cifre inserite, in ordine e senza buchi (da 0 a 6). */
  valore: string
  onChange: (valore: string) => void
  errore?: string
  ref?: Ref<CodiceSeiCifreHandle>
}

/**
 * Codice a 6 cifre in sei caselle (schermata Stitch "verifica-email").
 * Le cifre si riempiono in ordine, senza buchi: scrivere passa alla casella dopo, Backspace su una casella vuota
 * torna indietro, incollare (o l'autocompletamento del codice) riempie più caselle insieme. Si accettano solo cifre.
 */
function CodiceSeiCifre({ id, etichetta, valore, onChange, errore, ref }: Props) {
  const caselle = useRef<(HTMLInputElement | null)[]>([])
  // lunghezza aggiornata subito, senza aspettare il render: il cursore si sposta prima che arrivi il nuovo `valore`
  const lunghezza = useRef(valore.length)
  useEffect(() => {
    lunghezza.current = valore.length
  }, [valore])
  const idEtichetta = `${id}-etichetta`
  const idErrore = `${id}-errore`

  function vaiA(indice: number) {
    caselle.current[Math.max(0, Math.min(indice, CIFRE_CODICE - 1))]?.focus()
  }

  useImperativeHandle(ref, () => ({ focus: (dallInizio) => vaiA(dallInizio ? 0 : lunghezza.current) }), [])

  function aggiorna(nuovo: string) {
    lunghezza.current = nuovo.length
    onChange(nuovo)
  }

  function cambia(indice: number, evento: ChangeEvent<HTMLInputElement>) {
    const cifre = evento.target.value.replace(/\D/g, '')
    if (cifre === '') {
      // casella svuotata: si toglie la sua cifra e le successive scorrono indietro
      aggiorna(valore.slice(0, indice) + valore.slice(indice + 1))
      return
    }
    // la casella conteneva già una cifra: si tiene solo quella nuova
    const vecchia = valore[indice]
    const nuove = vecchia && cifre.length > 1 && cifre.includes(vecchia) ? cifre.replace(vecchia, '') : cifre
    const posizione = Math.min(indice, valore.length)
    const aggiornato = (valore.slice(0, posizione) + nuove + valore.slice(posizione + nuove.length)).slice(0, CIFRE_CODICE)
    aggiorna(aggiornato)
    vaiA(posizione + nuove.length)
  }

  function tasto(indice: number, evento: KeyboardEvent<HTMLInputElement>) {
    if (evento.key === 'Backspace' && !valore[indice] && indice > 0) {
      evento.preventDefault()
      aggiorna(valore.slice(0, indice - 1) + valore.slice(indice))
      vaiA(indice - 1)
    } else if (evento.key === 'ArrowLeft') {
      evento.preventDefault()
      vaiA(indice - 1)
    } else if (evento.key === 'ArrowRight') {
      evento.preventDefault()
      vaiA(Math.min(indice + 1, valore.length))
    }
  }

  return (
    <div className={stili.campo}>
      <span id={idEtichetta} className={stili.etichetta}>
        {etichetta}
      </span>
      <div
        role="group"
        aria-labelledby={idEtichetta}
        aria-describedby={errore ? idErrore : undefined}
        className={stili.caselle}
      >
        {Array.from({ length: CIFRE_CODICE }, (_, indice) => (
          <input
            key={indice}
            ref={(elemento) => {
              caselle.current[indice] = elemento
            }}
            id={indice === 0 ? id : undefined}
            type="text"
            inputMode="numeric"
            autoComplete={indice === 0 ? 'one-time-code' : 'off'}
            aria-label={`Cifra ${indice + 1} di ${CIFRE_CODICE}`}
            aria-invalid={errore ? true : undefined}
            value={valore[indice] ?? ''}
            onChange={(evento) => cambia(indice, evento)}
            onKeyDown={(evento) => tasto(indice, evento)}
            // niente buchi: una casella oltre l'ultima cifra rimanda alla prima vuota
            onFocus={() => indice > lunghezza.current && vaiA(lunghezza.current)}
            className={`${stili.casella} ${errore ? stili.casellaErrata : ''}`}
          />
        ))}
      </div>
      {errore && (
        <p id={idErrore} className={stili.errore}>
          {errore}
        </p>
      )}
    </div>
  )
}

export default CodiceSeiCifre
