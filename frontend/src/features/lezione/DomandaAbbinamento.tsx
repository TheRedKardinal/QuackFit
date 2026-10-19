import { numeri, quanteCoppie, type StatoAbbinamento } from './abbinamento'
import type { Coppia, DomandaLezione } from './tipi'
import stili from './lezione.module.css'

interface Props {
  domanda: DomandaLezione
  stato: StatoAbbinamento
  /** Dopo l'esito (o durante l'invio) le tessere non reagiscono più. */
  bloccata: boolean
  /** Coppie giuste, note solo dopo l'esito: ogni sinistro mostra ✓ o ✗. */
  soluzione: Coppia[] | null
  onToccaSinistro: (id: string) => void
  onToccaDestro: (id: string) => void
}

/**
 * Domanda di ABBINAMENTO: due colonne di tessere, a sinistra e a destra (mescolate dal backend ognuna per conto suo).
 * Si tocca un sinistro e poi un destro: le due tessere collegate mostrano lo stesso numero (1, 2, 3… nell'ordine dei
 * sinistri, vedi abbinamento.ts). Lo stato non è affidato al solo colore: numero, aria-pressed ed etichette.
 */
function DomandaAbbinamento({ domanda, stato, bloccata, soluzione, onToccaSinistro, onToccaDestro }: Props) {
  const sinistri = domanda.sinistra.map((voce) => voce.id)
  const numero = numeri(stato, sinistri)
  const giusto = (sinistroId: string) => soluzione?.find((coppia) => coppia.sinistroId === sinistroId)?.destroId

  return (
    <div>
      <p className={stili.contatoreCoppie} aria-live="polite">
        {quanteCoppie(stato)}/{sinistri.length} coppie
      </p>
      <div className={stili.abbinamento}>
        <ul className={stili.colonna} aria-label="Elementi da abbinare">
          {domanda.sinistra.map((voce) => {
            const n = numero[voce.id]
            const esito = soluzione ? (giusto(voce.id) === stato.coppie[voce.id] ? 'giusta' : 'sbagliata') : null
            const classi = [
              stili.tessera,
              stato.selezionato === voce.id ? stili.scelta : '',
              esito === 'giusta' ? stili.giusta : '',
              esito === 'sbagliata' ? stili.sbagliata : '',
            ]
              .filter(Boolean)
              .join(' ')
            return (
              <li key={voce.id}>
                <button
                  type="button"
                  className={classi}
                  aria-pressed={stato.selezionato === voce.id}
                  aria-label={`${voce.testo}${n ? `, coppia ${n}` : ''}${esito ? `, ${esito}` : ''}`}
                  disabled={bloccata}
                  onClick={() => onToccaSinistro(voce.id)}
                >
                  <span className={stili.testoOpzione}>{voce.testo}</span>
                  <span className={stili.numeroCoppia} aria-hidden="true">
                    {esito === 'giusta' ? '✓' : esito === 'sbagliata' ? '✗' : (n ?? '·')}
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
        <ul className={stili.colonna} aria-label="Elementi con cui abbinarli">
          {domanda.destra.map((voce) => {
            const n = numero[voce.id]
            return (
              <li key={voce.id}>
                <button
                  type="button"
                  className={`${stili.tessera} ${n ? stili.collegata : ''}`}
                  aria-label={`${voce.testo}${n ? `, coppia ${n}` : ''}`}
                  disabled={bloccata || stato.selezionato === null}
                  onClick={() => onToccaDestro(voce.id)}
                >
                  <span className={stili.numeroCoppia} aria-hidden="true">
                    {n ?? '·'}
                  </span>
                  <span className={stili.testoOpzione}>{voce.testo}</span>
                </button>
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}

export default DomandaAbbinamento
