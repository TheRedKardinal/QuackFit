/**
 * Collegamenti di una domanda di ABBINAMENTO mentre l'utente li sceglie (frontend/CLAUDE.md, Lezione): solo funzioni
 * pure, senza React. Regole (le stesse del backend, tentativi.md, Risposta): ogni sinistro ha al massimo un destro e
 * ogni destro al massimo un sinistro; si invia solo quando tutti i sinistri sono collegati.
 * - si tocca un sinistro (selezionato) e poi un destro: diventano una coppia
 * - toccare un sinistro già collegato scioglie la sua coppia e lo seleziona di nuovo
 * - scegliere un destro già usato lo sposta sul nuovo sinistro: il sinistro precedente resta senza coppia
 */

export interface StatoAbbinamento {
  /** sinistroId → destroId */
  coppie: Record<string, string>
  /** Sinistro in attesa del suo destro. */
  selezionato: string | null
}

export const abbinamentoVuoto: StatoAbbinamento = { coppie: {}, selezionato: null }

export function toccaSinistro(stato: StatoAbbinamento, sinistroId: string): StatoAbbinamento {
  if (stato.coppie[sinistroId] !== undefined) {
    const coppie = { ...stato.coppie }
    delete coppie[sinistroId]
    return { coppie, selezionato: sinistroId }
  }
  // toccare di nuovo il sinistro selezionato lo deseleziona
  return { ...stato, selezionato: stato.selezionato === sinistroId ? null : sinistroId }
}

export function toccaDestro(stato: StatoAbbinamento, destroId: string): StatoAbbinamento {
  if (stato.selezionato === null) {
    return stato
  }
  const coppie: Record<string, string> = {}
  for (const [sinistro, destro] of Object.entries(stato.coppie)) {
    if (destro !== destroId) {
      coppie[sinistro] = destro
    }
  }
  coppie[stato.selezionato] = destroId
  return { coppie, selezionato: null }
}

/** Coppie fatte, per il contatore "x/y coppie". */
export function quanteCoppie(stato: StatoAbbinamento): number {
  return Object.keys(stato.coppie).length
}

export function completa(stato: StatoAbbinamento, sinistri: string[]): boolean {
  return sinistri.every((sinistro) => stato.coppie[sinistro] !== undefined)
}

/**
 * Numero di ogni tessera collegata (lo stesso sul sinistro e sul suo destro): 1, 2, 3… nell'ordine dei sinistri,
 * ricalcolato ogni volta, quindi mai duplicati né buchi.
 */
export function numeri(stato: StatoAbbinamento, sinistri: string[]): Record<string, number> {
  const risultato: Record<string, number> = {}
  let prossimo = 1
  for (const sinistro of sinistri) {
    const destro = stato.coppie[sinistro]
    if (destro !== undefined) {
      risultato[sinistro] = prossimo
      risultato[destro] = prossimo
      prossimo++
    }
  }
  return risultato
}

/** Le coppie da inviare, nell'ordine dei sinistri. */
export function coppieDaInviare(stato: StatoAbbinamento, sinistri: string[]) {
  return sinistri
    .filter((sinistro) => stato.coppie[sinistro] !== undefined)
    .map((sinistro) => ({ sinistroId: sinistro, destroId: stato.coppie[sinistro] }))
}
