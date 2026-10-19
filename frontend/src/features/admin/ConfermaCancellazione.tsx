import Drawer from '../../common/Drawer'
import Pulsante from '../../common/Pulsante'
import TestoPulsante from '../../common/TestoPulsante'
import stili from './admin.module.css'

interface Props {
  /** Domanda da mostrare; null = pannello chiuso. */
  domanda: string | null
  inviando: boolean
  onConferma: () => void
  onAnnulla: () => void
}

/**
 * Conferma di una cancellazione nel Drawer rosso (mai window.confirm): cancellare chiede sempre conferma
 * (frontend/CLAUDE.md, Admin). Durante l'invio Elimina è disattivato e Esc non chiude.
 */
function ConfermaCancellazione({ domanda, inviando, onConferma, onAnnulla }: Props) {
  return (
    <Drawer aperto={domanda !== null} tono="errore" titolo="Conferma eliminazione" onChiudi={inviando ? undefined : onAnnulla}>
      <p>{domanda}</p>
      <div className={stili.azioniConferma}>
        <Pulsante variante="pericolo" onClick={onConferma} disabled={inviando}>
          <TestoPulsante testo="Elimina" inviando={inviando} secondiAttesa={0} />
        </Pulsante>
        <Pulsante variante="neutro" onClick={onAnnulla} disabled={inviando}>
          Annulla
        </Pulsante>
      </div>
    </Drawer>
  )
}

export default ConfermaCancellazione
