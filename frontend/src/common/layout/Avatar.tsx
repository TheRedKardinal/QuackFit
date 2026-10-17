import { Link } from 'react-router-dom'
import type { MeResponse } from '../../api/tipi'
import stili from './layout.module.css'

interface Props {
  utente: MeResponse
}

/** Iniziali di nome e cognome, per chi non ha ancora una foto. */
function iniziali(nome: string, cognome: string): string {
  return `${nome.charAt(0)}${cognome.charAt(0)}`.toUpperCase()
}

/** Avatar della barra in alto: la foto profilo se c'è, altrimenti le iniziali. Porta al profilo. */
function Avatar({ utente }: Props) {
  return (
    <Link to="/profile" className={stili.avatar} aria-label="Il tuo profilo">
      {utente.fotoProfilo ? (
        <img src={utente.fotoProfilo} alt="" className={stili.fotoAvatar} />
      ) : (
        <span aria-hidden="true">{iniziali(utente.nome, utente.cognome)}</span>
      )}
    </Link>
  )
}

export default Avatar
