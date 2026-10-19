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

/** Una lezione nella vista admin (LezioneAdminResponse): stessa forma nell'elenco e nelle risposte. */
export interface LezioneAdmin {
  id: string
  titolo: string
  descrizione: string
  ordine: number
  tappaId: string
  numeroDomande: number
}

/** Una tappa nella vista admin (TappaAdminResponse), con le sue lezioni attive in ordine. */
export interface TappaAdmin {
  id: string
  titolo: string
  descrizione: string
  ordine: number
  lezioni: LezioneAdmin[]
}

/** Body di creazione e modifica di tappe e lezioni; in modifica ogni campo è facoltativo. */
export interface DatiContenutoRequest {
  titolo?: string
  descrizione?: string
  ordine?: number
}

// --- domande (admin.md, Domande e Formato di una domanda) ---

export type TipoDomanda = 'VERO_FALSO' | 'SCELTA_MULTIPLA' | 'ABBINAMENTO'

export interface OpzioneAdmin {
  id: string
  testo: string
  corretta: boolean
}

export interface ElementoAdmin {
  id: string
  testo: string
}

export interface CoppiaAdmin {
  sinistro: ElementoAdmin
  destro: ElementoAdmin
}

/** Una domanda con le soluzioni: `opzioni` e `coppie` ci sono sempre, quella che non riguarda il tipo è []. */
export interface DomandaAdmin {
  id: string
  testo: string
  spiegazione: string
  tipo: TipoDomanda
  ordine: number
  opzioni: OpzioneAdmin[]
  coppie: CoppiaAdmin[]
}

/** GET /api/admin/lessons/{lessonId}. */
export interface LezioneDettaglioAdmin {
  id: string
  titolo: string
  descrizione: string
  ordine: number
  tappaId: string
  domande: DomandaAdmin[]
}

/** POST /api/admin/lessons/{lessonId}/questions: solo i campi del tipo scelto. */
export interface CreazioneDomandaRequest {
  testo: string
  spiegazione: string
  tipo: TipoDomanda
  rispostaVero?: boolean
  opzioni?: { testo: string; corretta: boolean }[]
  coppie?: { sinistra: string; destra: string }[]
}

/** PATCH /api/admin/questions/{questionId}: solo i testi. */
export interface ModificaDomandaRequest {
  testo: string
  spiegazione: string
  opzioni?: { id: string; testo: string }[]
  elementi?: { id: string; testo: string }[]
}
