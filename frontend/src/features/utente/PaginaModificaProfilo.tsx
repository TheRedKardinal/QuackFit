import { Link } from 'react-router-dom'
import SezioneCambioEmail from './SezioneCambioEmail'
import SezioneCambioPassword from './SezioneCambioPassword'
import SezioneCancellazione from './SezioneCancellazione'
import SezioneDatiPersonali from './SezioneDatiPersonali'
import SezioneFoto from './SezioneFoto'
import stili from './profilo.module.css'

/**
 * Modifica profilo (schermata Stitch "modifica-profilo"): foto, dati personali, password, email e cancellazione
 * dell'account, nello stesso ordine della schermata.
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
      <SezioneFoto />
      <SezioneDatiPersonali />
      <SezioneCambioPassword />
      <SezioneCambioEmail />
      <SezioneCancellazione />
    </section>
  )
}

export default PaginaModificaProfilo
