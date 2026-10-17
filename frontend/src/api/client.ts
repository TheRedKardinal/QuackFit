import { ApiError } from './ApiError'

export interface OpzioniRichiesta {
  metodo?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  /** Oggetto da inviare come JSON, oppure FormData per i file (multipart). */
  corpo?: unknown
  /** Token JWT della sessione: se c'è, si invia nell'header Authorization. */
  token?: string | null
}

/**
 * Unico punto da cui passano le chiamate al backend (frontend/CLAUDE.md, Chiamate al backend).
 * Restituisce il JSON della risposta (undefined per le risposte senza corpo, es. 204);
 * per ogni errore lancia un ApiError nel formato del backend, oppure ERRORE_RETE se il server non risponde.
 */
export async function richiesta<T>(percorso: string, opzioni: OpzioniRichiesta = {}): Promise<T> {
  const { metodo = 'GET', corpo, token } = opzioni
  const headers: Record<string, string> = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let body: BodyInit | undefined
  if (corpo instanceof FormData) {
    // il browser imposta da solo Content-Type con il boundary del multipart
    body = corpo
  } else if (corpo !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(corpo)
  }

  let risposta: Response
  try {
    risposta = await fetch(`${import.meta.env.VITE_API_URL ?? ''}${percorso}`, { method: metodo, headers, body })
  } catch {
    throw new ApiError({
      status: 0,
      codice: 'ERRORE_RETE',
      messaggio: 'Impossibile contattare il server',
      campi: {},
    })
  }

  const testo = await risposta.text()
  if (!risposta.ok) {
    throw erroreDallaRisposta(risposta.status, testo)
  }
  if (testo === '') {
    return undefined as T
  }
  try {
    return JSON.parse(testo) as T
  } catch {
    throw erroreGenerico(risposta.status)
  }
}

/** Legge il corpo d'errore del backend; se non è nel formato atteso (es. un proxy), errore generico. */
function erroreDallaRisposta(status: number, testo: string): ApiError {
  try {
    const json: unknown = JSON.parse(testo)
    if (typeof json === 'object' && json !== null && typeof (json as { codice?: unknown }).codice === 'string') {
      const errore = json as { codice: string; messaggio?: unknown; campi?: unknown }
      return new ApiError({
        status,
        codice: errore.codice,
        messaggio: typeof errore.messaggio === 'string' ? errore.messaggio : '',
        campi: typeof errore.campi === 'object' && errore.campi !== null ? (errore.campi as Record<string, string>) : {},
      })
    }
  } catch {
    // corpo non JSON: si ricade nell'errore generico
  }
  return erroreGenerico(status)
}

function erroreGenerico(status: number): ApiError {
  return new ApiError({ status, codice: 'ERRORE_INTERNO', messaggio: '', campi: {} })
}
