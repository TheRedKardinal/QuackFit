import type { ReactNode } from 'react'
import stili from './Scheda.module.css'

interface Props {
  children: ReactNode
  className?: string
}

/** Card "tattile": superficie bianca con il bordo inferiore in rilievo, come nelle schermate Stitch. */
function Scheda({ children, className }: Props) {
  return <div className={`${stili.scheda} ${className ?? ''}`}>{children}</div>
}

export default Scheda
