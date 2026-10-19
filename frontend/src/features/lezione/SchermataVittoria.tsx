import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import stili from './lezione.module.css'

interface Props {
  /** Streak dalla risposta di complete (calcolata dal backend, mai qui). */
  streak: { valore: number; record: number }
  onContinua: () => void
}

/**
 * Vittoria, versione essenziale: solo dati e azioni per prop. La task 45 aggiunge Coach Quack contento, il tracker
 * della settimana, il tempo impiegato e "Rivedi risposte" come blocchi nuovi.
 */
function SchermataVittoria({ streak, onContinua }: Props) {
  return (
    <section className={stili.fine}>
      <h1 className={stili.titoloFine}>Lezione completata!</h1>
      <Scheda>
        <p className={stili.valoreFine}>Streak: {streak.valore === 1 ? '1 giorno' : `${streak.valore} giorni`}</p>
        <p className="mb-0">Record: {streak.record === 1 ? '1 giorno' : `${streak.record} giorni`}</p>
      </Scheda>
      <Pulsante variante="successo" larghezzaPiena onClick={onContinua}>
        Continua
      </Pulsante>
    </section>
  )
}

export default SchermataVittoria
