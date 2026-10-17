import { messaggioErrore } from './messaggiErrore'
import stili from './MessaggioErrore.module.css'

interface Props {
  /** Codice di errore del backend (o ERRORE_RETE): si mostra la sua traduzione. */
  codice?: string
  /** Testo già pronto, al posto della traduzione del codice. */
  testo?: string | null
}

/**
 * Messaggio d'errore generale di una schermata (quelli dei campi stanno sotto i campi).
 * role="alert": lo screen reader lo legge appena compare. Senza codice né testo non mostra nulla.
 */
function MessaggioErrore({ codice, testo }: Props) {
  const contenuto = testo ?? (codice ? messaggioErrore(codice) : null)
  if (!contenuto) {
    return null
  }
  return (
    <p role="alert" className={stili.errore}>
      {contenuto}
    </p>
  )
}

export default MessaggioErrore
