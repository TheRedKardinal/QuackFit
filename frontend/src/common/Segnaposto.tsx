import type { ReactNode } from 'react'

interface Props {
  titolo: string
  children?: ReactNode
}

/** Pagina provvisoria (dentro LayoutApp) per le rotte le cui schermate arrivano nelle prossime task (la lezione, task 43). */
function Segnaposto({ titolo, children }: Props) {
  return (
    <section>
      <h1 className="h3 fw-bold">{titolo}</h1>
      <p>Schermata in arrivo.</p>
      {children}
    </section>
  )
}

export default Segnaposto
