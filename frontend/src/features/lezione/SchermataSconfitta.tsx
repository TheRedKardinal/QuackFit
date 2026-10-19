import CoachQuack from '../../common/CoachQuack'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import RiquadroRisultato from './RiquadroRisultato'
import stili from './lezione.module.css'

interface Props {
  errori: number
  tempo: string | null
  /** "Riprova" in corso: il pulsante si disattiva. */
  riavviando: boolean
  /** Codice d'errore di un "Riprova" non riuscito. */
  errore: string | null
  onRiprova: () => void
  onMappa: () => void
}

/**
 * Sconfitta (terzo errore): solo dati e azioni per prop, la logica sta in PaginaLezione. Coach Quack triste, riquadro
 * con esito, errori e tempo, "Riprova" (nuovo tentativo) e "Torna alla mappa". "Streak al sicuro" e i suggerimenti
 * dello Stitch sono fuori perimetro.
 */
function SchermataSconfitta({ errori, tempo, riavviando, errore, onRiprova, onMappa }: Props) {
  return (
    <section className={stili.fine}>
      <CoachQuack stato="triste" className={stili.mascotteFine} />
      <h1 className={stili.titoloFine}>Lezione non superata</h1>
      <p>Rivedi con calma i concetti e riprova quando vuoi.</p>
      <RiquadroRisultato esito="Non superata" errori={errori} tempo={tempo} />
      <MessaggioErrore codice={errore ?? undefined} />
      <div className={stili.azioniFine}>
        <Pulsante larghezzaPiena disabled={riavviando} onClick={onRiprova}>
          {riavviando ? 'Avvio…' : 'Riprova'}
        </Pulsante>
        <Pulsante variante="neutro" larghezzaPiena onClick={onMappa}>
          Torna alla mappa
        </Pulsante>
      </div>
    </section>
  )
}

export default SchermataSconfitta
