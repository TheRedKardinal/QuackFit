import { datiErrore } from '../../api/ApiError'
import { richiesta } from '../../api/client'
import { createAppAsyncThunk } from '../../store/hooks'
import { rileggiProfilo } from '../../store/sessione/sessioneSlice'
import type { ChiusuraTentativo, ContenutoLezione, EsitoRisposta, RispostaRequest } from './tipi'

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

/**
 * Una risposta alla volta (POST /api/attempts/{id}/answers): l'opzione per Vero/Falso e Scelta multipla, tutte le
 * coppie insieme per l'abbinamento. Il corpo contiene solo il campo del tipo.
 */
export const inviaRisposta = createAppAsyncThunk(
  'lezione/inviaRisposta',
  async (risposta: RispostaRequest, { getState, rejectWithValue }) => {
    const { sessione, lezione } = getState()
    try {
      const esito = await richiesta<EsitoRisposta>(`/api/attempts/${lezione.tentativoId}/answers`, {
        metodo: 'POST',
        corpo: risposta,
        token: sessione.token,
      })
      // l'istante dell'esito: con il terzo errore è la fine della lezione (tempo mostrato nella sconfitta)
      return { esito, istante: Date.now() }
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/**
 * Chiusura come superata (POST /api/attempts/{id}/complete): 400 se non tutte le domande sono risolte. Con il 200 parte
 * la rilettura silenziosa del profilo (streak e lezioni completate di /api/me nella barra e nel profilo).
 */
export const chiudiTentativo = createAppAsyncThunk(
  'lezione/chiudiTentativo',
  async (_: void, { getState, dispatch, rejectWithValue }) => {
    const { sessione, lezione } = getState()
    try {
      const chiusura = await richiesta<ChiusuraTentativo>(`/api/attempts/${lezione.tentativoId}/complete`, {
        metodo: 'POST',
        token: sessione.token,
      })
      void dispatch(rileggiProfilo())
      return { chiusura, istante: Date.now() }
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/**
 * "Riprova" dopo una sconfitta: nuovo tentativo sulla stessa lezione (POST /api/attempts). È una copia dell'avvio della
 * mappa: una feature non importa gli interni di un'altra. Restituisce anche l'istante di inizio per il timer e la
 * streak di /api/me prima della lezione (per "+1 OGGI").
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
      return { tentativoId, lezioneId, inizio: Date.now(), streakPrima: getState().sessione.utente?.streak.valore ?? 0 }
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)
