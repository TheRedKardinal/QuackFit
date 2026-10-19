import { datiErrore } from '../../api/ApiError'
import { richiesta, type OpzioniRichiesta } from '../../api/client'
import { createAppAsyncThunk } from '../../store/hooks'
import type {
  CreazioneDomandaRequest,
  DatiContenutoRequest,
  LezioneDettaglioAdmin,
  ModificaDomandaRequest,
  Pagina,
  RuoloAssegnabile,
  TappaAdmin,
  UtenteAdmin,
} from './tipi'

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

// --- tappe e lezioni (admin.md, Tappe e Lezioni) ---

/** Elenco di tutte le tappe con le loro lezioni (GET /api/admin/stages). */
export const caricaTappe = createAppAsyncThunk('admin/caricaTappe', async (_: void, { getState, rejectWithValue }) => {
  try {
    return await richiesta<TappaAdmin[]>('/api/admin/stages', { token: getState().sessione.token })
  } catch (errore) {
    return rejectWithValue(datiErrore(errore))
  }
})

/**
 * Le operazioni sui contenuti hanno tutte la stessa forma: una chiamata e, se fallisce, l'errore del backend.
 * La pagina poi rilegge l'elenco (creazione, cancellazione e riordino cambiano anche gli altri elementi).
 */
function operazione<A>(nome: string, chiamata: (argomento: A) => [string, OpzioniRichiesta]) {
  return createAppAsyncThunk(`admin/${nome}`, async (argomento: A, { getState, rejectWithValue }) => {
    const [percorso, opzioni] = chiamata(argomento)
    try {
      return await richiesta<{ id: string } | undefined>(percorso, { ...opzioni, token: getState().sessione.token })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  })
}

export const creaTappa = operazione('creaTappa', (dati: DatiContenutoRequest) => [
  '/api/admin/stages',
  { metodo: 'POST', corpo: dati },
])

export const modificaTappa = operazione('modificaTappa', ({ id, dati }: { id: string; dati: DatiContenutoRequest }) => [
  `/api/admin/stages/${id}`,
  { metodo: 'PATCH', corpo: dati },
])

export const cancellaTappa = operazione('cancellaTappa', (id: string) => [
  `/api/admin/stages/${id}`,
  { metodo: 'DELETE' },
])

export const creaLezione = operazione(
  'creaLezione',
  ({ tappaId, dati }: { tappaId: string; dati: DatiContenutoRequest }) => [
    `/api/admin/stages/${tappaId}/lessons`,
    { metodo: 'POST', corpo: dati },
  ],
)

export const modificaLezione = operazione(
  'modificaLezione',
  ({ id, dati }: { id: string; dati: DatiContenutoRequest }) => [
    `/api/admin/lessons/${id}`,
    { metodo: 'PATCH', corpo: dati },
  ],
)

export const cancellaLezione = operazione('cancellaLezione', (id: string) => [
  `/api/admin/lessons/${id}`,
  { metodo: 'DELETE' },
])

// --- domande (admin.md, Domande) ---

/** Dettaglio di una lezione con tutte le sue domande e le soluzioni (GET /api/admin/lessons/{lessonId}). */
export const caricaLezione = createAppAsyncThunk(
  'admin/caricaLezione',
  async (id: string, { getState, rejectWithValue }) => {
    try {
      return await richiesta<LezioneDettaglioAdmin>(`/api/admin/lessons/${id}`, { token: getState().sessione.token })
    } catch (errore) {
      return rejectWithValue(datiErrore(errore))
    }
  },
)

export const creaDomanda = operazione(
  'creaDomanda',
  ({ lezioneId, dati }: { lezioneId: string; dati: CreazioneDomandaRequest }) => [
    `/api/admin/lessons/${lezioneId}/questions`,
    { metodo: 'POST', corpo: dati },
  ],
)

export const modificaDomanda = operazione(
  'modificaDomanda',
  ({ id, dati }: { id: string; dati: ModificaDomandaRequest }) => [
    `/api/admin/questions/${id}`,
    { metodo: 'PATCH', corpo: dati },
  ],
)

export const cancellaDomanda = operazione('cancellaDomanda', (id: string) => [
  `/api/admin/questions/${id}`,
  { metodo: 'DELETE' },
])
