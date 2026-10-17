import { useEffect, useId, useRef, type ReactNode } from 'react'
import stili from './Drawer.module.css'

export type TonoDrawer = 'successo' | 'errore'

interface Props {
  aperto: boolean
  tono: TonoDrawer
  titolo: string
  children: ReactNode
  /** Chiusura con il tasto Esc; se manca, il drawer si chiude solo dalle azioni al suo interno. */
  onChiudi?: () => void
}

/**
 * Pannello che sale dal basso (es. esito di una risposta nella lezione, task 43): verde per il successo,
 * rosso per l'errore. Il tono non è affidato al solo colore: c'è sempre il titolo, con un simbolo.
 * All'apertura il focus passa al pannello, così tastiera e screen reader ci arrivano subito.
 */
function Drawer({ aperto, tono, titolo, children, onChiudi }: Props) {
  const idTitolo = useId()
  const pannello = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (aperto) {
      pannello.current?.focus()
    }
  }, [aperto])

  useEffect(() => {
    if (!aperto || !onChiudi) {
      return
    }
    function tasto(evento: KeyboardEvent) {
      if (evento.key === 'Escape') {
        onChiudi?.()
      }
    }
    document.addEventListener('keydown', tasto)
    return () => document.removeEventListener('keydown', tasto)
  }, [aperto, onChiudi])

  if (!aperto) {
    return null
  }

  return (
    <div
      ref={pannello}
      role="dialog"
      aria-labelledby={idTitolo}
      tabIndex={-1}
      className={`${stili.drawer} ${tono === 'successo' ? stili.successo : stili.errore}`}
    >
      <div className={stili.contenuto}>
        <h2 id={idTitolo} className={stili.titolo}>
          <span aria-hidden="true">{tono === 'successo' ? '✓' : '✗'}</span> {titolo}
        </h2>
        {children}
      </div>
    </div>
  )
}

export default Drawer
