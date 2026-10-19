import { MAX_ERRORI } from './codaLezione'
import stili from './lezione.module.css'

interface Props {
  esito: 'Superata' | 'Non superata'
  errori: number
  /** Tempo impiegato già formattato; null se non noto. */
  tempo: string | null
}

/** Riquadro di fine lezione: esito, errori x/3 e tempo impiegato (timer del frontend). */
function RiquadroRisultato({ esito, errori, tempo }: Props) {
  return (
    <dl className={stili.risultato}>
      <div className={stili.voceRisultato}>
        <dt>Esito</dt>
        <dd>{esito}</dd>
      </div>
      <div className={stili.voceRisultato}>
        <dt>Errori</dt>
        <dd>
          {errori}/{MAX_ERRORI}
        </dd>
      </div>
      {tempo && (
        <div className={stili.voceRisultato}>
          <dt>Tempo</dt>
          <dd>{tempo}</dd>
        </div>
      )}
    </dl>
  )
}

export default RiquadroRisultato
