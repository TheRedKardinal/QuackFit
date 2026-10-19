/**
 * Tracker della settimana nella vittoria (frontend/CLAUDE.md, Vittoria): solo funzioni pure. Non è una streak calcolata
 * dal frontend: si **deduce** dal valore che il backend ha appena restituito con complete, sapendo che l'ultima lezione
 * è di oggi. Con streak N si colorano oggi e gli N-1 giorni precedenti, solo quelli della settimana corrente
 * (da lunedì a domenica, nel fuso del browser).
 */

export const GIORNI_SETTIMANA = ['L', 'M', 'M', 'G', 'V', 'S', 'D'] as const

/** Posizione del giorno nella settimana italiana: lunedì 0 … domenica 6 (getDay() parte dalla domenica). */
export function indiceGiorno(data: Date): number {
  return (data.getDay() + 6) % 7
}

export interface GiornoTracker {
  lettera: string
  /** Giorno della streak (colorato). */
  attivo: boolean
  oggi: boolean
}

export function trackerSettimana(oggi: Date, valoreStreak: number): GiornoTracker[] {
  const indiceOggi = indiceGiorno(oggi)
  return GIORNI_SETTIMANA.map((lettera, indice) => ({
    lettera,
    attivo: indice <= indiceOggi && indiceOggi - indice < valoreStreak,
    oggi: indice === indiceOggi,
  }))
}
