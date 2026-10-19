import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'

interface Props {
  onAvanti: () => void
}

/**
 * PROVVISORIO (task 44): al posto delle domande di ABBINAMENTO, finché non c'è la loro interfaccia. "Avanti" non invia
 * nulla, quindi a fine coda la chiusura risponde 400. La task 44 lo toglie e prova nel browser la chiusura riuscita.
 */
function DomandaInArrivo({ onAvanti }: Props) {
  return (
    <Scheda>
      <p className="fw-bold">Domanda in arrivo</p>
      <p>Questo tipo di domanda sarà disponibile a breve.</p>
      <Pulsante variante="neutro" larghezzaPiena onClick={onAvanti}>
        Avanti
      </Pulsante>
    </Scheda>
  )
}

export default DomandaInArrivo
