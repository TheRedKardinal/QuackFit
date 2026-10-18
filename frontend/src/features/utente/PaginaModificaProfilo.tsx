import { Link } from 'react-router-dom'
import SezioneCambioPassword from './SezioneCambioPassword'
import SezioneDatiPersonali from './SezioneDatiPersonali'
import stili from './profilo.module.css'

/**
 * Modifica profilo (schermata Stitch "modifica-profilo"): dati personali e password.
 * Foto, cambio email e cancellazione dell'account arrivano nella task 28.
 */
function PaginaModificaProfilo() {
  return (
    <section>
      <div className={stili.barraModifica}>
        <h1 className={stili.titoloPagina}>Modifica profilo</h1>
        <Link to="/profile" className={stili.link}>
          Torna al profilo
        </Link>
      </div>
      <SezioneDatiPersonali />
      <SezioneCambioPassword />
    </section>
  )
}

export default PaginaModificaProfilo
