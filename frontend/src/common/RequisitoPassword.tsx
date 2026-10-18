import stili from './RequisitoPassword.module.css'

interface Props {
  ok: boolean
  testo: string
}

/** Voce del riquadro "Requisiti di sicurezza": simbolo e testo, non solo il colore. */
function RequisitoPassword({ ok, testo }: Props) {
  return (
    <li className={ok ? stili.requisitoOk : undefined}>
      <span aria-hidden="true">{ok ? '✓' : '○'}</span> {testo}
      <span className="visually-hidden">{ok ? ' (rispettato)' : ' (non ancora rispettato)'}</span>
    </li>
  )
}

export default RequisitoPassword
