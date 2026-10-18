import { datiErrore } from '../../api/ApiError'
import { richiesta } from '../../api/client'
import { createAppAsyncThunk } from '../../store/hooks'
import { emailConfermata, impostaToken, logout } from '../../store/sessione/sessioneSlice'
import type {
  LoginRequest,
  MessaggioResponse,
  PasswordDimenticataRequest,
  ReimpostaPasswordRequest,
  RegistrazioneRequest,
  TokenResponse,
  VerificaEmailRequest,
} from './tipi'

/**
 * Thunk dell'accesso; gli errori tornano al form con unwrap().
 * Registrazione e login, se riescono, salvano il token (impostaToken): la sessione carica /api/me e le guardie
 * portano alla schermata del codice (email non verificata) o alla mappa.
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

/** Verifica dell'email con il codice (utente loggato, anche non verificato). Se riesce, le guardie portano alla mappa. */
export const verificaEmail = createAppAsyncThunk(
  'auth/verificaEmail',
  async (dati: VerificaEmailRequest, { dispatch, getState, rejectWithValue }) => {
    try {
      await richiesta<void>('/api/me/verification', { metodo: 'POST', corpo: dati, token: getState().sessione.token })
      dispatch(emailConfermata())
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/** Nuovo codice di verifica: entro 60 secondi dal precedente il backend risponde 429 ATTENDI_REINVIO. */
export const reinviaCodice = createAppAsyncThunk(
  'auth/reinviaCodice',
  async (_: void, { getState, rejectWithValue }) => {
    try {
      await richiesta<void>('/api/me/verification/resend', { metodo: 'POST', token: getState().sessione.token })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/** Password dimenticata: il backend risponde sempre 200 con lo stesso messaggio, che l'email esista o no. */
export const passwordDimenticata = createAppAsyncThunk(
  'auth/passwordDimenticata',
  async (dati: PasswordDimenticataRequest, { rejectWithValue }) => {
    try {
      await richiesta<MessaggioResponse>('/api/auth/password/forgot', { metodo: 'POST', corpo: dati })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/**
 * Nuova password con il codice. Se riesce, il backend fa logout ovunque: si cancella anche un eventuale token salvato
 * in questo browser (ormai non valido), così la guardia del login non rimanda alla mappa.
 */
export const reimpostaPassword = createAppAsyncThunk(
  'auth/reimpostaPassword',
  async (dati: ReimpostaPasswordRequest, { dispatch, rejectWithValue }) => {
    try {
      await richiesta<void>('/api/auth/password/reset', { metodo: 'POST', corpo: dati })
      dispatch(logout())
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)
