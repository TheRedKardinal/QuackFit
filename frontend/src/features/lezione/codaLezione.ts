/**
 * Coda delle domande di una lezione (frontend/CLAUDE.md, Lezione): solo funzioni pure, senza React né Redux.
 * Le domande si fanno nell'ordine in cui arrivano; le sbagliate si ripropongono a fine giro, finché non sono risolte o
 * finché il tentativo non fallisce. Esito ed errori li decide il backend: qui si sceglie solo la prossima domanda.
 */

/** Errori ammessi: al terzo il backend chiude il tentativo come FALLITO (serve solo per l'indicatore "Errori x/3"). */
export const MAX_ERRORI = 3

export interface Coda {
  /** Domande del giro in corso; la prima è quella mostrata. */
  daFare: string[]
  /** Sbagliate in questo giro: tornano nel giro successivo. */
  daRiproporre: string[]
}

export function codaIniziale(idDomande: string[]): Coda {
  return { daFare: [...idDomande], daRiproporre: [] }
}

export function domandaCorrente(coda: Coda): string | null {
  return coda.daFare[0] ?? null
}

/**
 * Dopo l'esito della domanda corrente: esce dalla coda e, se sbagliata, va tra quelle da riproporre. Finito il giro,
 * le sbagliate diventano il giro successivo.
 */
export function avanza(coda: Coda, corretta: boolean): Coda {
  const [corrente, ...resto] = coda.daFare
  if (corrente === undefined) {
    return coda
  }
  const daRiproporre = corretta ? coda.daRiproporre : [...coda.daRiproporre, corrente]
  return resto.length > 0 ? { daFare: resto, daRiproporre } : { daFare: daRiproporre, daRiproporre: [] }
}

/** Nessuna domanda rimasta: si può chiudere il tentativo. */
export function finita(coda: Coda): boolean {
  return coda.daFare.length === 0
}

/** Domande già superate (per la barra di avanzamento). */
export function risolte(coda: Coda, totale: number): number {
  return totale - coda.daFare.length - coda.daRiproporre.length
}
