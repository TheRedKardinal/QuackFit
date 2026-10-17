import { isRejectedWithValue, type ListenerMiddlewareInstance } from '@reduxjs/toolkit'
import { isDatiErrore } from '../../api/ApiError'
import { emailNonVerificata, impostaToken, logout } from './sessioneSlice'
import { cancellaToken, salvaToken } from './tokenSalvato'

/**
 * Effetti della sessione, fuori dai reducer (che devono restare puri):
 * - il token si salva e si cancella in localStorage insieme allo stato
 * - qualunque thunk rifiutato con 401 NON_AUTENTICATO fa logout; con 403 EMAIL_NON_VERIFICATA segna l'email
 *   come non verificata. Lo spostamento di pagina lo fanno le guardie delle rotte, che reagiscono allo stato.
 */
export function registraListenerSessione(listener: ListenerMiddlewareInstance): void {
  listener.startListening({
    actionCreator: impostaToken,
    effect: (action) => salvaToken(action.payload),
  })

  listener.startListening({
    actionCreator: logout,
    effect: () => cancellaToken(),
  })

  listener.startListening({
    matcher: isRejectedWithValue,
    effect: (action, api) => {
      if (!isDatiErrore(action.payload)) {
        return
      }
      if (action.payload.codice === 'NON_AUTENTICATO') {
        api.dispatch(logout())
      } else if (action.payload.codice === 'EMAIL_NON_VERIFICATA') {
        api.dispatch(emailNonVerificata())
      }
    },
  })
}
