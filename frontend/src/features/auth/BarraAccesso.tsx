import stili from './accesso.module.css'

interface Props {
  titolo: string
}

/**
 * Barra in alto delle pagine di accesso, come quella dell'app: il marchio in evidenza e sotto il nome della pagina
 * (che resta l'h1 della pagina, per gli screen reader).
 */
function BarraAccesso({ titolo }: Props) {
  return (
    <header className={stili.barra}>
      <span className={stili.marchio}>QuackFit</span>
      <h1 className={stili.titoloBarra}>{titolo}</h1>
    </header>
  )
}

export default BarraAccesso
