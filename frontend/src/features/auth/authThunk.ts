import { datiErrore } from '../../api/ApiError'
import { richiesta } from '../../api/client'
import { createAppAsyncThunk } from '../../store/hooks'
import { impostaToken } from './sessioneSlice'
import type { LoginRequest, RegistrazioneRequest, TokenResponse } from './tipi'

/**
 * Registrazione e login. Se riescono salvano il token (impostaToken): la sessione carica /api/me e le guardie
 * portano alla schermata del codice (email non verificata) o alla mappa. Gli errori tornano al form con unwrap().
 */
export const registra = createAppAsyncThunk(
  'auth/registra',
  async (dati: RegistrazioneRequest, { dispatch, rejectWithValue }) => {
    try {
      const { token } = await richiesta<TokenResponse>('/api/auth/register', { metodo: 'POST', corpo: dati })
      dispatch(impostaToken(token))
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

export const accedi = createAppAsyncThunk('auth/accedi', async (dati: LoginRequest, { dispatch, rejectWithValue }) => {
  try {
    const { token } = await richiesta<TokenResponse>('/api/auth/login', { metodo: 'POST', corpo: dati })
    dispatch(impostaToken(token))
  } catch (errore) {
    return rejectWithValue(datiErrore(errore))
  }
})
