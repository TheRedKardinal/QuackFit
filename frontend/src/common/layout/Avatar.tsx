import { Link } from 'react-router-dom'
import type { MeResponse } from '../../api/tipi'
import FotoProfilo from '../FotoProfilo'
import stili from './layout.module.css'

interface Props {
  utente: MeResponse
}

/** Avatar della barra in alto: la foto profilo se c'è, altrimenti le iniziali. Porta al profilo. */
function Avatar({ utente }: Props) {
  return (
    <Link to="/profile" className={stili.avatar} aria-label="Il tuo profilo">
      <FotoProfilo utente={utente} />
    </Link>
  )
}

export default Avatar
