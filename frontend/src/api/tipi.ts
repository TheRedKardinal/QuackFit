// Tipi delle risposte usate da più feature; i nomi dei campi sono identici al JSON del backend.

export type Ruolo = 'UTENTE' | 'ADMIN' | 'SUPERADMIN'

export interface StreakResponse {
  valore: number
  record: number
}

/** GET /api/me (backend/docs/specs/me.md, Dati del profilo). */
export interface MeResponse {
  nome: string
  cognome: string
  email: string
  ruolo: Ruolo
  fotoProfilo: string | null
  emailVerificata: boolean
  streak: StreakResponse
  lezioniCompletate: number
}
