import Pulsante from '../../common/Pulsante'
import { useAppDispatch } from '../../store/hooks'
import { logout } from './sessioneSlice'

/** Logout volontario: cancella token e stato; le guardie riportano al login. */
function PulsanteEsci() {
  const dispatch = useAppDispatch()

  return (
    <Pulsante variante="neutro" onClick={() => dispatch(logout())}>
      Esci
    </Pulsante>
  )
}

export default PulsanteEsci
