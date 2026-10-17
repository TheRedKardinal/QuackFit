// Utilità condivise dai test della sessione e delle guardie (non usate dall'app).
import { vi } from 'vitest'
import type { DatiErrore } from '../../api/ApiError'
import type { MeResponse } from '../../api/tipi'
import { createAppAsyncThunk } from '../../store/hooks'

export const fetchFinto = vi.fn<typeof fetch>()

export function profilo(modifiche: Partial<MeResponse> = {}): MeResponse {
  return {
    nome: 'Mario',
    cognome: 'Rossi',
    email: 'mario@example.com',
    ruolo: 'UTENTE',
    fotoProfilo: null,
    emailVerificata: true,
    streak: { valore: 2, record: 5 },
    lezioniCompletate: 3,
    ...modifiche,
  }
}

export function rispostaJson(status: number, corpo: unknown): Response {
  return new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } })
}

export function rispostaErrore(status: number, codice: string): Response {
  return rispostaJson(status, { status, codice, errore: '', messaggio: 'dal backend', campi: {}, timestamp: '' })
}

/** Un thunk qualunque di una feature che fallisce con il codice dato: per provare la reazione globale della sessione. */
export const thunkCheFallisce = createAppAsyncThunk('prova/fallisce', async (codice: string, { rejectWithValue }) => {
  const errore: DatiErrore = { status: codice === 'NON_AUTENTICATO' ? 401 : 403, codice, messaggio: '', campi: {} }
  return rejectWithValue(errore)
})
