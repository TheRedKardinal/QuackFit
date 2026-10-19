import { trackerSettimana } from './tracker'
import stili from './lezione.module.css'

interface Props {
  valoreStreak: number
  /** Passato da fuori così i test usano una data fissa. */
  oggi: Date
}

const NOMI_GIORNO = ['lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato', 'domenica']

/** Settimana L-M-M-G-V-S-D con i giorni della streak colorati (dedotti dal valore, vedi tracker.ts). */
function TrackerSettimana({ valoreStreak, oggi }: Props) {
  return (
    <ol className={stili.tracker} aria-label="Giorni di streak di questa settimana">
      {trackerSettimana(oggi, valoreStreak).map((giorno, indice) => (
        <li
          key={indice}
          className={`${stili.giorno} ${giorno.attivo ? stili.giornoAttivo : ''} ${giorno.oggi ? stili.giornoOggi : ''}`}
        >
          <span aria-hidden="true">{giorno.lettera}</span>
          <span className="visually-hidden">
            {NOMI_GIORNO[indice]}
            {giorno.oggi ? ' (oggi)' : ''}: {giorno.attivo ? 'streak' : 'nessuna lezione'}
          </span>
        </li>
      ))}
    </ol>
  )
}

export default TrackerSettimana
