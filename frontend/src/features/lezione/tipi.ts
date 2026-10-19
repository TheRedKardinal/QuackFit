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

export interface CoppiaSoluzione {
  sinistroId: string
  destroId: string
}

/** POST /api/attempts/{id}/answers → 200: la soluzione arriva solo qui, dopo la risposta. */
export interface EsitoRisposta {
  corretta: boolean
  soluzione: { opzioneId: string | null; coppie: CoppiaSoluzione[] }
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
