import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { StatoLezione } from './tipi'
import stili from './mappa.module.css'

interface Punto {
  x: number
  y: number
}

interface Props {
  /** Stato di ogni lezione della tappa, nell'ordine dei nodi. */
  stati: StatoLezione[]
  children: ReactNode
}

/**
 * Sentiero tratteggiato che unisce i nodi di una tappa, come nello Stitch (solo grafica, `aria-hidden`).
 * Le posizioni si misurano dai nodi (`data-nodo`), così il sentiero segue lo zig-zag e le etichette di altezza
 * variabile; si rimisura quando cambia la dimensione della lista (ResizeObserver, che jsdom non ha: nei test niente
 * sentiero). Ogni tratto è verde se porta a una lezione raggiungibile, grigio se porta a una bloccata.
 */
function SentieroTappa({ stati, children }: Props) {
  const contenitore = useRef<HTMLDivElement | null>(null)
  const [punti, setPunti] = useState<Punto[]>([])
  const [dimensioni, setDimensioni] = useState({ larghezza: 0, altezza: 0 })
  // chiave stabile degli stati: un array nuovo a ogni render rifarebbe l'effetto (e le misure) all'infinito
  const chiaveStati = stati.join(',')

  useEffect(() => {
    const elemento = contenitore.current
    if (!elemento || typeof ResizeObserver === 'undefined') {
      return
    }
    // il primo richiamo arriva subito dopo observe(): nessuna misura sincrona dentro l'effetto
    const osservatore = new ResizeObserver(() => {
      const base = elemento.getBoundingClientRect()
      const nodi = [...elemento.querySelectorAll<HTMLElement>('[data-nodo]')]
      setPunti(
        nodi.map((nodo) => {
          const r = nodo.getBoundingClientRect()
          return { x: r.left - base.left + r.width / 2, y: r.top - base.top + r.height / 2 }
        }),
      )
      setDimensioni({ larghezza: base.width, altezza: base.height })
    })
    osservatore.observe(elemento)
    return () => osservatore.disconnect()
  }, [chiaveStati])

  return (
    <div ref={contenitore} className={stili.contenitoreSentiero}>
      {punti.length > 1 && (
        <svg
          className={stili.sentiero}
          width={dimensioni.larghezza}
          height={dimensioni.altezza}
          aria-hidden="true"
          focusable="false"
        >
          {punti.slice(1).map((arrivo, indice) => {
            const partenza = punti[indice]
            const meta = (partenza.y + arrivo.y) / 2
            return (
              <path
                key={indice}
                d={`M ${partenza.x} ${partenza.y} C ${partenza.x} ${meta}, ${arrivo.x} ${meta}, ${arrivo.x} ${arrivo.y}`}
                className={stati[indice + 1] === 'BLOCCATA' ? stili.trattoBloccato : stili.trattoRaggiunto}
              />
            )
          })}
        </svg>
      )}
      {children}
    </div>
  )
}

export default SentieroTappa
