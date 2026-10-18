import { combineReducers, configureStore, createListenerMiddleware, type UnknownAction } from '@reduxjs/toolkit'
import { registraListenerSessione } from './sessione/sessioneListener'
import sessioneReducer, { logout, statoSessioneVuoto } from './sessione/sessioneSlice'
import { leggiToken } from './sessione/tokenSalvato'

const reducerApp = combineReducers({
  sessione: sessioneReducer,
})

export type RootState = ReturnType<typeof reducerApp>

/** Al logout tutto lo stato riparte da zero, anche quello delle feature aggiunte in futuro. */
function reducerRadice(stato: RootState | undefined, action: UnknownAction): RootState {
  return reducerApp(logout.match(action) ? undefined : stato, action)
}

/**
 * Crea lo store con il token di partenza (di default quello salvato in localStorage).
 * È una funzione così i test creano uno store nuovo per ogni caso.
 */
export function creaStore(token: string | null = leggiToken()) {
  const listener = createListenerMiddleware()
  registraListenerSessione(listener)
  return configureStore({
    reducer: reducerRadice,
    preloadedState: { sessione: { ...statoSessioneVuoto, token } },
    middleware: (getDefaultMiddleware) => getDefaultMiddleware().prepend(listener.middleware),
  })
}

export type AppStore = ReturnType<typeof creaStore>
export type AppDispatch = AppStore['dispatch']
