/**
 * Codici di errore del backend (backend/CLAUDE.md, Errori) più ERRORE_RETE, che esiste solo nel frontend:
 * la richiesta non è arrivata al server o la risposta non è arrivata a noi (status 0).
 */
export const CODICI_ERRORE = [
  'VALIDAZIONE',
  'CODICE_NON_VALIDO',
  'CODICE_SCADUTO',
  'TROPPI_TENTATIVI',
  'PASSWORD_ATTUALE_ERRATA',
  'FILE_NON_VALIDO',
  'NON_AUTENTICATO',
  'CREDENZIALI_NON_VALIDE',
  'ACCESSO_NEGATO',
  'EMAIL_NON_VERIFICATA',
  'LEZIONE_BLOCCATA',
  'NON_TROVATO',
  'METODO_NON_CONSENTITO',
  'EMAIL_GIA_USATA',
  'TENTATIVO_CHIUSO',
  'ATTENDI_REINVIO',
  'TROPPE_RICHIESTE',
  'ERRORE_INTERNO',
  'ERRORE_RETE',
] as const

export type CodiceErrore = (typeof CODICI_ERRORE)[number]

/**
 * Dati di un errore in forma serializzabile: è ciò che i thunk passano a Redux con rejectWithValue
 * (lo store accetta solo oggetti semplici, non istanze di classi).
 */
export interface DatiErrore {
  status: number
  codice: string
  messaggio: string
  campi: Record<string, string>
}

/** Errore di una chiamata al backend: la logica decide sempre in base a `codice`, mai al testo del messaggio. */
export class ApiError extends Error {
  readonly status: number
  readonly codice: string
  readonly messaggio: string
  readonly campi: Record<string, string>

  constructor(dati: DatiErrore) {
    super(dati.messaggio)
    this.name = 'ApiError'
    this.status = dati.status
    this.codice = dati.codice
    this.messaggio = dati.messaggio
    this.campi = dati.campi
  }

  dati(): DatiErrore {
    return { status: this.status, codice: this.codice, messaggio: this.messaggio, campi: this.campi }
  }
}

/** Qualunque errore catturato in un thunk diventa DatiErrore; un errore inatteso conta come ERRORE_INTERNO. */
export function datiErrore(errore: unknown): DatiErrore {
  if (errore instanceof ApiError) {
    return errore.dati()
  }
  return { status: 0, codice: 'ERRORE_INTERNO', messaggio: 'Errore imprevisto', campi: {} }
}

/** Controlla che un valore (es. il payload di un'azione rifiutata) abbia la forma di DatiErrore. */
export function isDatiErrore(valore: unknown): valore is DatiErrore {
  return (
    typeof valore === 'object' &&
    valore !== null &&
    typeof (valore as DatiErrore).codice === 'string' &&
    typeof (valore as DatiErrore).status === 'number'
  )
}
