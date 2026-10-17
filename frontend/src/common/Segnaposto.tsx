import type { ReactNode } from 'react'

interface Props {
  titolo: string
  children?: ReactNode
}

/** Pagina provvisoria per le rotte le cui schermate arrivano nelle prossime task (20-22, 27, 32). */
function Segnaposto({ titolo, children }: Props) {
  return (
    <main className="container py-4" style={{ maxWidth: 480 }}>
      <h1 className="h3">{titolo}</h1>
      <p>Schermata in arrivo.</p>
      {children}
    </main>
  )
}

export default Segnaposto
