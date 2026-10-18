import RequisitoPassword from './RequisitoPassword'
import stili from './RiquadroRequisiti.module.css'
import { requisitiPassword } from './validazione'

interface Props {
  password: string
}

/** Riquadro "Requisiti di sicurezza" che si spunta mentre si scrive la nuova password (solo un aiuto visivo). */
function RiquadroRequisiti({ password }: Props) {
  const requisiti = requisitiPassword(password)
  return (
    <section className={stili.riquadro} aria-label="Requisiti di sicurezza">
      <p className={stili.titolo}>Requisiti di sicurezza</p>
      <ul className={stili.requisiti}>
        <RequisitoPassword ok={requisiti.caratteri} testo="8+ caratteri" />
        <RequisitoPassword ok={requisiti.maiuscola} testo="1 maiuscola" />
        <RequisitoPassword ok={requisiti.numero} testo="1 numero" />
        <RequisitoPassword ok={requisiti.speciale} testo="1 speciale (!@#$)" />
      </ul>
    </section>
  )
}

export default RiquadroRequisiti
