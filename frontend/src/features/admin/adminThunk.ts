import { datiErrore } from '../../api/ApiError'
import { richiesta } from '../../api/client'
import { createAppAsyncThunk } from '../../store/hooks'
import type { Pagina, RuoloAssegnabile, UtenteAdmin } from './tipi'

interface ParametriLista {
  /** Pagina da 0. */
  pagina: number
  /** Testo da cercare, già ripulito; vuoto = nessun filtro. */
  q: string
}

/**
 * Lista degli utenti attivi (GET /api/admin/users). `size` non si invia: vale il default del backend (20),
 * scelta consapevole rispetto allo Stitch (vedi frontend/CLAUDE.md, Admin).
 */
export const caricaUtenti = createAppAsyncThunk(
  'admin/caricaUtenti',
  async ({ pagina, q }: ParametriLista, { getState, rejectWithValue }) => {
    const parametri = new URLSearchParams({ page: String(pagina) })
    if (q !== '') {
      parametri.set('q', q)
    }
    try {
      return await richiesta<Pagina<UtenteAdmin>>(`/api/admin/users?${parametri}`, {
        token: getState().sessione.token,
      })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

/** Cambio ruolo (PATCH /api/admin/users/{id}/role, 204): il backend fa logout ovunque per l'utente modificato. */
export const cambiaRuolo = createAppAsyncThunk(
  'admin/cambiaRuolo',
  async ({ id, ruolo }: { id: string; ruolo: RuoloAssegnabile }, { getState, rejectWithValue }) => {
    try {
      await richiesta<void>(`/api/admin/users/${id}/role`, {
        metodo: 'PATCH',
        corpo: { ruolo },
        token: getState().sessione.token,
      })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)
