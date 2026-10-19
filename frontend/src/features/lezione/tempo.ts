/** Durata della lezione in forma breve: "45s", "2m 45s", "1h 3m". Un valore negativo o mancante vale 0. */
export function formattaDurata(millisecondi: number): string {
  const secondiTotali = Math.max(0, Math.floor(millisecondi / 1000))
  const ore = Math.floor(secondiTotali / 3600)
  const minuti = Math.floor((secondiTotali % 3600) / 60)
  const secondi = secondiTotali % 60
  if (ore > 0) {
    return `${ore}h ${minuti}m`
  }
  if (minuti > 0) {
    return `${minuti}m ${secondi}s`
  }
  return `${secondi}s`
}

/** Tempo tra avvio e fine del tentativo; senza uno dei due istanti non si mostra. */
export function durata(inizio: number | null, fine: number | null): string | null {
  return inizio === null || fine === null ? null : formattaDurata(fine - inizio)
}
