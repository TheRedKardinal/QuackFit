import stili from './accesso.module.css'

interface Props {
  titolo: string
}

/** Barra in alto delle pagine di accesso: titolo della pagina e marchio. */
function BarraAccesso({ titolo }: Props) {
  return (
    <header className={stili.barra}>
      <h1 className={stili.titoloBarra}>{titolo}</h1>
      <span className={stili.marchio}>QuackFit</span>
    </header>
  )
}

export default BarraAccesso
