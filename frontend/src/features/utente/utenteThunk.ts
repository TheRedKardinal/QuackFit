import { datiErrore } from '../../api/ApiError'
import { richiesta } from '../../api/client'
import { strip } from '../../common/validazione'
import { createAppAsyncThunk } from '../../store/hooks'
import { datiAnagraficiAggiornati, fotoProfiloAggiornata } from '../../store/sessione/sessioneSlice'
import type {
  AvatarResponse,
  CambioEmailRequest,
  CambioPasswordRequest,
  CancellazioneAccountRequest,
  ConfermaCambioEmailRequest,
  ModificaDatiRequest,
} from './tipi'

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

/**
 * Foto profilo già ritagliata e compressa (common/immagine): multipart con il campo "file", senza Content-Type a mano.
 * Se riesce, l'URL nuovo va nel profilo in memoria (barra in alto e profilo si aggiornano).
 */
export const caricaFoto = createAppAsyncThunk(
  'utente/caricaFoto',
  async (foto: Blob, { dispatch, getState, rejectWithValue }) => {
    try {
      const corpo = new FormData()
      corpo.append('file', foto, 'avatar.jpg')
      const { fotoProfilo } = await richiesta<AvatarResponse>('/api/me/avatar', {
        metodo: 'PATCH',
        corpo,
        token: getState().sessione.token,
      })
      dispatch(fotoProfiloAggiornata(fotoProfilo))
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/** Richiesta del cambio email: il codice parte verso la NUOVA email; quella dell'utente non cambia ancora. */
export const richiediCambioEmail = createAppAsyncThunk(
  'utente/richiediCambioEmail',
  async (dati: CambioEmailRequest, { getState, rejectWithValue }) => {
    try {
      await richiesta<void>('/api/me/email', { metodo: 'POST', corpo: dati, token: getState().sessione.token })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/** Conferma con il codice (204 senza nuovo token, logout ovunque): logout e login li fa la pagina via /logout. */
export const confermaCambioEmail = createAppAsyncThunk(
  'utente/confermaCambioEmail',
  async (dati: ConfermaCambioEmailRequest, { getState, rejectWithValue }) => {
    try {
      await richiesta<void>('/api/me/email/confirm', { metodo: 'POST', corpo: dati, token: getState().sessione.token })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/** Cancellazione dell'account (204, logout ovunque): logout e login li fa la pagina via /logout. */
export const cancellaAccount = createAppAsyncThunk(
  'utente/cancellaAccount',
  async (dati: CancellazioneAccountRequest, { getState, rejectWithValue }) => {
    try {
      await richiesta<void>('/api/me', { metodo: 'DELETE', corpo: dati, token: getState().sessione.token })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)
