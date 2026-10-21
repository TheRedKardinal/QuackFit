import CoachQuack from '../../common/CoachQuack'
import Icona from '../../common/Icona'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import RiquadroRisultato from './RiquadroRisultato'
import TrackerSettimana from './TrackerSettimana'
import stili from './lezione.module.css'

interface Props {
  /** Streak dalla risposta di complete (calcolata dal backend, mai qui). */
  streak: { valore: number; record: number }
  /** La streak è salita rispetto a quella di /api/me prima della lezione: si mostra "+1 OGGI". */
  aumentata: boolean
  errori: number
  tempo: string | null
  /** Oggi, per il tracker (passato da fuori così i test usano una data fissa). */
  oggi: Date
  onContinua: () => void
  onRivediRisposte: () => void
}

function giorni(n: number): string {
  return n === 1 ? '1 giorno' : `${n} giorni`
}

/**
 * Vittoria: solo dati e azioni per prop, la logica sta in PaginaLezione. Coach Quack contento, la streak come numero
 * grande con la fiamma (è ciò che fa tornare domani), record, tracker della settimana, riquadro con tempo ed errori
 * (l'esito non si ripete: lo dice già il titolo), "Continua" (mappa) e "Rivedi risposte".
 * XP, "100% Focus" e la "Pillola di Coach Quack" dello Stitch sono fuori perimetro.
 */
function SchermataVittoria({ streak, aumentata, errori, tempo, oggi, onContinua, onRivediRisposte }: Props) {
  return (
    <section className={stili.fine}>
      <CoachQuack stato="contento" className={stili.mascotteFine} />
      <h1 className={stili.titoloFine}>Lezione completata!</h1>
      <Scheda>
        <div className={stili.rigaStreak}>
          <p className={stili.eroeStreak}>
            <span className={stili.fiammaStreak}>
              <Icona nome="fiamma" dimensione={40} />
            </span>
            <span className={stili.numeroStreak}>{streak.valore}</span>{' '}
            <span className={stili.unitaStreak}>{streak.valore === 1 ? 'giorno' : 'giorni'} di streak</span>
          </p>
          {aumentata && <span className={stili.piuUno}>+1 oggi</span>}
        </div>
        <p className={stili.recordStreak}>Record: {giorni(streak.record)}</p>
        <TrackerSettimana valoreStreak={streak.valore} oggi={oggi} />
      </Scheda>
      <RiquadroRisultato errori={errori} tempo={tempo} />
      <div className={stili.azioniFine}>
        <Pulsante variante="successo" larghezzaPiena onClick={onContinua}>
          Continua
        </Pulsante>
        <Pulsante variante="neutro" larghezzaPiena onClick={onRivediRisposte}>
          Rivedi risposte
        </Pulsante>
      </div>
    </section>
  )
}

export default SchermataVittoria
