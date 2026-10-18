import type { MeResponse } from '../api/tipi'
import stili from './FotoProfilo.module.css'

interface Props {
  utente: Pick<MeResponse, 'nome' | 'cognome' | 'fotoProfilo'>
  /** Lato del cerchio, in rem. */
  dimensione?: number
}

/** Iniziali di nome e cognome, per chi non ha ancora una foto. */
function iniziali(nome: string, cognome: string): string {
  return `${nome.charAt(0)}${cognome.charAt(0)}`.toUpperCase()
}

/** Foto profilo rotonda; senza foto, le iniziali. Decorativa: il nome è sempre scritto accanto o nell'etichetta. */
function FotoProfilo({ utente, dimensione = 2.5 }: Props) {
  return (
    <span
      className={stili.foto}
      style={{ width: `${dimensione}rem`, height: `${dimensione}rem`, fontSize: `${dimensione * 0.4}rem` }}
      aria-hidden="true"
    >
      {utente.fotoProfilo ? (
        <img src={utente.fotoProfilo} alt="" className={stili.immagine} />
      ) : (
        iniziali(utente.nome, utente.cognome)
      )}
    </span>
  )
}

export default FotoProfilo
