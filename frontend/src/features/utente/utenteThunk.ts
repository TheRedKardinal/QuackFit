import { datiErrore } from '../../api/ApiError'
import { richiesta } from '../../api/client'
import { strip } from '../../common/validazione'
import { createAppAsyncThunk } from '../../store/hooks'
import { datiAnagraficiAggiornati } from '../../store/sessione/sessioneSlice'
import type { CambioPasswordRequest, ModificaDatiRequest } from './tipi'

/**
 * Nome e cognome (PATCH /api/me, 204). Se riesce, il profilo in memoria si aggiorna con i valori puliti come li salva
 * il backend (strip): niente nuova chiamata a /api/me.
 */
export const modificaDati = createAppAsyncThunk(
  'utente/modificaDati',
  async (dati: ModificaDatiRequest, { dispatch, getState, rejectWithValue }) => {
    try {
      await richiesta<void>('/api/me', { metodo: 'PATCH', corpo: dati, token: getState().sessione.token })
      dispatch(datiAnagraficiAggiornati({ nome: strip(dati.nome), cognome: strip(dati.cognome) }))
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/**
 * Cambio password (PATCH /api/me/password, 204 senza token: il backend fa logout ovunque).
 * Il logout locale e il rimando al login li fa la pagina, nello stesso momento (vedi PaginaModificaProfilo).
 */
export const cambiaPassword = createAppAsyncThunk(
  'utente/cambiaPassword',
  async (dati: CambioPasswordRequest, { getState, rejectWithValue }) => {
    try {
      await richiesta<void>('/api/me/password', { metodo: 'PATCH', corpo: dati, token: getState().sessione.token })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)
