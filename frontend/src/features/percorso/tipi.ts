/** Tipi della mappa (percorso.md, Mappa): i nomi dei campi sono quelli del JSON del backend. */

/** Calcolato dal backend: il frontend lo legge e non lo ricalcola mai. */
export type StatoLezione = 'COMPLETATA' | 'SBLOCCATA' | 'BLOCCATA'

export interface LezionePercorso {
  id: string
  titolo: string
  descrizione: string
  ordine: number
  stato: StatoLezione
}

export interface TappaPercorso {
  id: string
  titolo: string
  descrizione: string
  ordine: number
  lezioni: LezionePercorso[]
}

/** POST /api/attempts → 201. */
export interface AvvioTentativo {
  tentativoId: string
}
