import { useAppDispatch } from '../../store/hooks'
import { logout } from './sessioneSlice'

/** Logout volontario: cancella token e stato; le guardie riportano al login. */
function PulsanteEsci() {
  const dispatch = useAppDispatch()

  return (
    <button type="button" className="btn-push btn-push-neutro" onClick={() => dispatch(logout())}>
      Esci
    </button>
  )
}

export default PulsanteEsci
