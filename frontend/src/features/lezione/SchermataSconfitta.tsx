import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import { MAX_ERRORI } from './codaLezione'
import stili from './lezione.module.css'

interface Props {
  errori: number
  /** "Riprova" in corso: il pulsante si disattiva. */
  riavviando: boolean
  /** Codice d'errore di un "Riprova" non riuscito. */
  errore: string | null
  onRiprova: () => void
  onMappa: () => void
}

/**
 * Sconfitta (terzo errore), versione essenziale: solo dati e azioni per prop, la logica sta in PaginaLezione.
 * La task 45 aggiunge Coach Quack triste e il tempo impiegato come blocchi nuovi.
 */
function SchermataSconfitta({ errori, riavviando, errore, onRiprova, onMappa }: Props) {
  return (
    <section className={stili.fine}>
      <h1 className={stili.titoloFine}>Lezione non superata</h1>
      <Scheda>
        <p className={stili.valoreFine}>
          Errori {errori}/{MAX_ERRORI}
        </p>
        <p className="mb-0">Rivedi con calma i concetti e riprova quando vuoi.</p>
      </Scheda>
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
