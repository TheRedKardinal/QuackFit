/** Tipi della lezione (percorso.md, Contenuto di una lezione; tentativi.md): i nomi sono quelli del JSON del backend. */

export type TipoDomanda = 'VERO_FALSO' | 'SCELTA_MULTIPLA' | 'ABBINAMENTO'

export type EsitoTentativo = 'IN_CORSO' | 'SUPERATO' | 'FALLITO'

/** Un'opzione o un elemento di un abbinamento. */
export interface Voce {
  id: string
  testo: string
}

/** Senza soluzioni: `opzioni`, `sinistra` e `destra` ci sono sempre, `[]` quando non riguardano il tipo. */
export interface DomandaLezione {
  id: string
  testo: string
  tipo: TipoDomanda
  opzioni: Voce[]
  sinistra: Voce[]
  destra: Voce[]
}

/** GET /api/path/lessons/{id}: le domande arrivano già mescolate. */
export interface ContenutoLezione {
  id: string
  titolo: string
  descrizione: string
  domande: DomandaLezione[]
}

/** Una coppia di un abbinamento: nella risposta inviata e nella soluzione. */
export interface Coppia {
  sinistroId: string
  destroId: string
}

/** POST /api/attempts/{id}/answers: solo il campo del tipo (opzione oppure tutte le coppie insieme). */
export type RispostaRequest = { domandaId: string; opzioneId: string } | { domandaId: string; coppie: Coppia[] }

/** POST /api/attempts/{id}/answers → 200: la soluzione arriva solo qui, dopo la risposta. */
export interface EsitoRisposta {
  corretta: boolean
  soluzione: { opzioneId: string | null; coppie: Coppia[] }
  spiegazione: string
  errori: number
  esito: EsitoTentativo
}

/** POST /api/attempts/{id}/complete → 200. */
export interface ChiusuraTentativo {
  esito: EsitoTentativo
  errori: number
  streak: { valore: number; record: number }
}

/** GET /api/attempts/{id} (tentativi.md, Resoconto): solo le domande già risposte, nell'ordine dell'admin. */
export interface Resoconto {
  id: string
  esito: EsitoTentativo
  errori: number
  iniziatoAt: string
  /** null se il tentativo è in corso. */
  finitoAt: string | null
  lezione: { id: string; titolo: string }
  domande: DomandaResoconto[]
}

/**
 * La prima risposta data. `risposta` e `soluzione` sono null per l'abbinamento; `coppie` è vuota per le domande a
 * opzioni. `corretta`: la prima risposta era giusta.
 */
export interface DomandaResoconto {
  id: string
  testo: string
  tipo: TipoDomanda
  spiegazione: string
  corretta: boolean
  risposta: Voce | null
  soluzione: Voce | null
  coppie: CoppiaResoconto[]
}

/** Una coppia della prima risposta, con il destro giusto per quel sinistro. */
export interface CoppiaResoconto {
  sinistro: Voce
  destro: Voce
  soluzione: Voce
  corretta: boolean
}
