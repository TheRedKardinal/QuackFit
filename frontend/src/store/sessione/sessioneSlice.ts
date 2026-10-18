import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import { datiErrore, type DatiErrore } from '../../api/ApiError'
import { richiesta } from '../../api/client'
import type { MeResponse } from '../../api/tipi'
import { createAppAsyncThunk } from '../hooks'

export type StatoCaricamento = 'inattivo' | 'in_corso' | 'riuscito' | 'fallito'

export interface StatoSessione {
  token: string | null
  /** Profilo da GET /api/me: finché non è caricato le pagine protette non si mostrano. */
  utente: MeResponse | null
  caricamento: StatoCaricamento
  errore: DatiErrore | null
}

/** Stato senza sessione. Il token salvato si passa allo store alla creazione (vedi store.ts), non si legge qui. */
export const statoSessioneVuoto: StatoSessione = {
  token: null,
  utente: null,
  caricamento: 'inattivo',
  errore: null,
}

/** Carica il profilo dell'utente loggato. Non parte se un caricamento è già in corso (es. doppio effetto in StrictMode). */
export const caricaProfilo = createAppAsyncThunk(
  'sessione/caricaProfilo',
  async (_: void, { getState, rejectWithValue }) => {
    try {
      return await richiesta<MeResponse>('/api/me', { token: getState().sessione.token })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
  { condition: (_, { getState }) => getState().sessione.caricamento !== 'in_corso' },
)

const sessioneSlice = createSlice({
  name: 'sessione',
  initialState: statoSessioneVuoto,
  reducers: {
    /** Nuovo token (login o registrazione): il profilo si ricarica con il nuovo utente. */
    impostaToken(_, action: PayloadAction<string>) {
      return { ...statoSessioneVuoto, token: action.payload }
    },
    /** Logout: lo store riparte da zero (vedi store.ts); il listener cancella il token salvato. */
    logout() {
      return statoSessioneVuoto
    },
    /** Codice di verifica accettato (204): l'email è verificata e le guardie lasciano passare, senza ricaricare /api/me. */
    emailConfermata(stato) {
      if (stato.utente) {
        stato.utente.emailVerificata = true
      }
    },
    /** Nome e cognome modificati (PATCH /api/me, 204): si aggiorna il profilo in memoria, senza ricaricare /api/me. */
    datiAnagraficiAggiornati(stato, action: PayloadAction<{ nome: string; cognome: string }>) {
      if (stato.utente) {
        stato.utente.nome = action.payload.nome
        stato.utente.cognome = action.payload.cognome
      }
    },
    /** Il backend ha risposto 403 EMAIL_NON_VERIFICATA: le guardie portano alla schermata del codice. */
    emailNonVerificata(stato) {
      if (stato.utente) {
        stato.utente.emailVerificata = false
      }
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(caricaProfilo.pending, (stato) => {
        stato.caricamento = 'in_corso'
        stato.errore = null
      })
      .addCase(caricaProfilo.fulfilled, (stato, action) => {
        stato.caricamento = 'riuscito'
        stato.utente = action.payload
      })
      .addCase(caricaProfilo.rejected, (stato, action) => {
        stato.caricamento = 'fallito'
        stato.errore = action.payload ?? datiErrore(action.error)
      })
  },
})

export const { impostaToken, logout, emailConfermata, datiAnagraficiAggiornati, emailNonVerificata } =
  sessioneSlice.actions
export default sessioneSlice.reducer
