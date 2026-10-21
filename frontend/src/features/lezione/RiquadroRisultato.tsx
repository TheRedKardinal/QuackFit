import { MAX_ERRORI } from './codaLezione'
import stili from './lezione.module.css'

interface Props {
  /** Solo nella sconfitta: nella vittoria l'esito lo dice già il titolo "Lezione completata!". */
  esito?: 'Non superata'
  errori: number
  /** Tempo impiegato già formattato; null se non noto. */
  tempo: string | null
}

/** Riquadro di fine lezione: esito (solo nella sconfitta), tempo impiegato (timer del frontend) ed errori x/3. */
function RiquadroRisultato({ esito, errori, tempo }: Props) {
  return (
    <dl className={stili.risultato}>
      {esito && (
        <div className={stili.voceRisultato}>
          <dt>Esito</dt>
          <dd>{esito}</dd>
        </div>
      )}
      {tempo && (
        <div className={stili.voceRisultato}>
          <dt>Tempo</dt>
          <dd>{tempo}</dd>
        </div>
      )}
      <div className={stili.voceRisultato}>
        <dt>Errori</dt>
        <dd>
          {errori}/{MAX_ERRORI}
        </dd>
      </div>
    </dl>
  )
}

export default RiquadroRisultato
