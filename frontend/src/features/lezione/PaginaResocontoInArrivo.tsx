import { useNavigate } from 'react-router-dom'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'

/**
 * PROVVISORIO (task 46): segnaposto di "Rivedi risposte" su /attempts/:id finché non c'è il resoconto vero. La task 46
 * lo sostituisce con il resoconto (GET /api/attempts/{id}) e prova il flusso nel browser.
 */
function PaginaResocontoInArrivo() {
  const navigate = useNavigate()
  return (
    <section>
      <h1 className="h3 fw-bold">Resoconto</h1>
      <Scheda>
        <p>Resoconto in arrivo: presto potrai rivedere qui le tue risposte.</p>
        <Pulsante variante="neutro" larghezzaPiena onClick={() => navigate('/')}>
          Torna alla mappa
        </Pulsante>
      </Scheda>
    </section>
  )
}

export default PaginaResocontoInArrivo
