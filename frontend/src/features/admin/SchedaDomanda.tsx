import Pulsante from '../../common/Pulsante'
import { NOMI_TIPO } from './nomiTipo'
import type { DomandaAdmin } from './tipi'
import stili from './admin.module.css'

interface Props {
  domanda: DomandaAdmin
  onModifica: () => void
  onElimina: () => void
}

/**
 * Una domanda con le soluzioni (solo admin): la risposta giusta ha un simbolo e la scritta "Corretta", non solo il
 * colore; per l'abbinamento le coppie giuste sinistra ⇄ destra.
 */
function SchedaDomanda({ domanda, onModifica, onElimina }: Props) {
  return (
    <article className={stili.domanda} aria-label={`Domanda ${domanda.ordine}`}>
      <div className={stili.intestazioneDomanda}>
        <span className={stili.tipoDomanda}>
          #{domanda.ordine} · {NOMI_TIPO[domanda.tipo]}
        </span>
        <span className={stili.azioniDomanda}>
          <Pulsante variante="neutro" aria-label={`Modifica i testi della domanda ${domanda.ordine}`} onClick={onModifica}>
            Modifica testi
          </Pulsante>
          <Pulsante variante="pericolo" aria-label={`Elimina la domanda ${domanda.ordine}`} onClick={onElimina}>
            Elimina
          </Pulsante>
        </span>
      </div>
      <p className={stili.testoDomanda}>{domanda.testo}</p>
      <p className={stili.spiegazione}>
        <span className={stili.etichettaSezione}>Spiegazione</span>
        {domanda.spiegazione}
      </p>
      {domanda.tipo === 'ABBINAMENTO' ? (
        <ul className={stili.soluzioni} aria-label="Coppie giuste">
          {domanda.coppie.map((coppia) => (
            <li key={coppia.sinistro.id} className={stili.coppia}>
              <span>{coppia.sinistro.testo}</span>
              <span aria-label="abbinato a">⇄</span>
              <span>{coppia.destro.testo}</span>
            </li>
          ))}
        </ul>
      ) : (
        <ul className={stili.soluzioni} aria-label="Opzioni">
          {domanda.opzioni.map((opzione) => (
            <li key={opzione.id} className={opzione.corretta ? `${stili.opzione} ${stili.corretta}` : stili.opzione}>
              <span>{opzione.testo}</span>
              {opzione.corretta && (
                <span className={stili.etichettaCorretta}>
                  <span aria-hidden="true">✓</span> Corretta
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </article>
  )
}

export default SchedaDomanda
