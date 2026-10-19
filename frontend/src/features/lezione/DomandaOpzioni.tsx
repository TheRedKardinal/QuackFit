import type { DomandaLezione } from './tipi'
import stili from './lezione.module.css'

interface Props {
  domanda: DomandaLezione
  selezionata: string | null
  /** Dopo l'esito (o durante l'invio) le opzioni non reagiscono più. */
  bloccata: boolean
  /** Opzione giusta, nota solo dopo l'esito: si segna in verde (e la scelta sbagliata in rosso). */
  soluzione: string | null
  onSeleziona: (opzioneId: string) => void
}

const LETTERE = ['A', 'B', 'C', 'D', 'E', 'F']

/**
 * Domanda VERO_FALSO (due pulsanti grandi) o SCELTA_MULTIPLA (selezione singola con le lettere). La scelta si conferma
 * con "Verifica" (in PaginaLezione). Lo stato non è affidato al solo colore: aria-pressed e "✓"/"✗" dopo l'esito.
 */
function DomandaOpzioni({ domanda, selezionata, bloccata, soluzione, onSeleziona }: Props) {
  const veroFalso = domanda.tipo === 'VERO_FALSO'
  return (
    <div className={veroFalso ? stili.veroFalso : stili.opzioni}>
      {domanda.opzioni.map((opzione, indice) => {
        const scelta = opzione.id === selezionata
        const giusta = soluzione !== null && opzione.id === soluzione
        const sbagliata = soluzione !== null && scelta && !giusta
        const classi = [
          stili.opzione,
          veroFalso ? stili.opzioneGrande : '',
          scelta ? stili.scelta : '',
          giusta ? stili.giusta : '',
          sbagliata ? stili.sbagliata : '',
        ]
          .filter(Boolean)
          .join(' ')
        return (
          <button
            key={opzione.id}
            type="button"
            className={classi}
            aria-pressed={scelta}
            disabled={bloccata}
            onClick={() => onSeleziona(opzione.id)}
          >
            {!veroFalso && (
              <span className={stili.lettera} aria-hidden="true">
                {LETTERE[indice]}
              </span>
            )}
            <span className={stili.testoOpzione}>{opzione.testo}</span>
            {giusta && <span aria-label="risposta giusta">✓</span>}
            {sbagliata && <span aria-label="risposta sbagliata">✗</span>}
          </button>
        )
      })}
    </div>
  )
}

export default DomandaOpzioni
