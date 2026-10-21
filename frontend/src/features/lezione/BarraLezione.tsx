import { MAX_ERRORI } from './codaLezione'
import stili from './lezione.module.css'

interface Props {
  risolte: number
  totale: number
  errori: number
  onChiudi: () => void
}

/**
 * Barra in alto della lezione: chiusura (esce senza chiamare il backend), avanzamento e "Errori x/3" con tre pallini.
 * Niente cuori né vite (fuori perimetro).
 */
function BarraLezione({ risolte, totale, errori, onChiudi }: Props) {
  const percentuale = totale === 0 ? 0 : Math.round((risolte / totale) * 100)
  return (
    <header className={stili.barra}>
      <button type="button" className={stili.chiudi} aria-label="Esci dalla lezione" onClick={onChiudi}>
        <span aria-hidden="true">✕</span>
      </button>
      <div
        className={stili.avanzamento}
        role="progressbar"
        aria-label="Domande risolte"
        aria-valuemin={0}
        aria-valuemax={totale}
        aria-valuenow={risolte}
      >
        <div className={stili.riempimento} style={{ transform: `translateX(${percentuale - 100}%)` }} />
      </div>
      <div className={stili.errori}>
        <span aria-hidden="true" className={stili.pallini}>
          {Array.from({ length: MAX_ERRORI }, (_, indice) => (
            <span key={indice} className={`${stili.pallino} ${indice < errori ? stili.pallinoPieno : ''}`} />
          ))}
        </span>
        Errori {errori}/{MAX_ERRORI}
      </div>
    </header>
  )
}

export default BarraLezione
