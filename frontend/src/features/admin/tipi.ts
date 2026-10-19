// Tipi della gestione utenti admin (backend/docs/specs/admin.md, Utenti); i campi sono identici al JSON del backend.
import type { Ruolo } from '../../api/tipi'

/** Un utente nella lista admin (UtenteAdminResponse). */
export interface UtenteAdmin {
  id: string
  nome: string
  cognome: string
  email: string
  ruolo: Ruolo
  emailVerificata: boolean
  creatoAt: string
}

/** Una pagina di risultati (PaginaResponse), con i numeri di pagina da 0. */
export interface Pagina<T> {
  contenuto: T[]
  pagina: number
  dimensione: number
  totaleElementi: number
  totalePagine: number
}

/** I soli ruoli che si possono assegnare: SUPERADMIN non si assegna mai. */
export type RuoloAssegnabile = 'UTENTE' | 'ADMIN'
