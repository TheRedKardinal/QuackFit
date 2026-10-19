import { datiErrore } from '../../api/ApiError'
import { richiesta } from '../../api/client'
import { createAppAsyncThunk } from '../../store/hooks'
import type { AvvioTentativo, TappaPercorso } from './tipi'

/** Mappa delle tappe con lo stato di ogni lezione per l'utente (GET /api/path/stages). */
export const caricaMappa = createAppAsyncThunk('percorso/caricaMappa', async (_: void, { getState, rejectWithValue }) => {
  try {
    return await richiesta<TappaPercorso[]>('/api/path/stages', { token: getState().sessione.token })
  } catch (errore) {
    return rejectWithValue(datiErrore(errore))
  }
})

/**
 * Avvio di una lezione (POST /api/attempts, 201 con `tentativoId`). Errori previsti (tentativi.md, Avvio):
 * 404 lezione cancellata o senza domande, 403 LEZIONE_BLOCCATA.
 */
export const avviaLezione = createAppAsyncThunk(
  'percorso/avviaLezione',
  async (lezioneId: string, { getState, rejectWithValue }) => {
    try {
      return await richiesta<AvvioTentativo>('/api/attempts', {
        metodo: 'POST',
        corpo: { lezioneId },
        token: getState().sessione.token,
      })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)
