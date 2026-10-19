import { datiErrore } from '../../api/ApiError'
import { richiesta } from '../../api/client'
import { createAppAsyncThunk } from '../../store/hooks'
import type { ChiusuraTentativo, ContenutoLezione, EsitoRisposta } from './tipi'

/** Domande della lezione, già mescolate dal backend (GET /api/path/lessons/{id}). */
export const caricaContenuto = createAppAsyncThunk(
  'lezione/caricaContenuto',
  async (lezioneId: string, { getState, rejectWithValue }) => {
    try {
      return await richiesta<ContenutoLezione>(`/api/path/lessons/${lezioneId}`, { token: getState().sessione.token })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/** Una risposta alla volta (POST /api/attempts/{id}/answers): opzione per Vero/Falso e Scelta multipla. */
export const inviaRisposta = createAppAsyncThunk(
  'lezione/inviaRisposta',
  async ({ domandaId, opzioneId }: { domandaId: string; opzioneId: string }, { getState, rejectWithValue }) => {
    const { sessione, lezione } = getState()
    try {
      return await richiesta<EsitoRisposta>(`/api/attempts/${lezione.tentativoId}/answers`, {
        metodo: 'POST',
        corpo: { domandaId, opzioneId },
        token: sessione.token,
      })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/** Chiusura come superata (POST /api/attempts/{id}/complete): 400 se non tutte le domande sono risolte. */
export const chiudiTentativo = createAppAsyncThunk(
  'lezione/chiudiTentativo',
  async (_: void, { getState, rejectWithValue }) => {
    const { sessione, lezione } = getState()
    try {
      return await richiesta<ChiusuraTentativo>(`/api/attempts/${lezione.tentativoId}/complete`, {
        metodo: 'POST',
        token: sessione.token,
      })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/**
 * "Riprova" dopo una sconfitta: nuovo tentativo sulla stessa lezione (POST /api/attempts). È una copia dell'avvio della
 * mappa: una feature non importa gli interni di un'altra. Restituisce anche l'istante di inizio per il timer.
 */
export const riavviaLezione = createAppAsyncThunk(
  'lezione/riavviaLezione',
  async (lezioneId: string, { getState, rejectWithValue }) => {
    try {
      const { tentativoId } = await richiesta<{ tentativoId: string }>('/api/attempts', {
        metodo: 'POST',
        corpo: { lezioneId },
        token: getState().sessione.token,
      })
      return { tentativoId, lezioneId, inizio: Date.now() }
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)
