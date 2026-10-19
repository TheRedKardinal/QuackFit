import { describe, expect, it } from 'vitest'
import { formattaDurata, durata } from './tempo'
import { indiceGiorno, trackerSettimana } from './tracker'

// date locali fisse (mese 9 = ottobre 2026): lunedì 12, mercoledì 14, domenica 18
const LUNEDI = new Date(2026, 9, 12, 10, 0)
const MERCOLEDI = new Date(2026, 9, 14, 10, 0)
const DOMENICA = new Date(2026, 9, 18, 23, 30)

const attivi = (oggi: Date, valore: number) => trackerSettimana(oggi, valore).map((g) => g.attivo)

describe('tracker della settimana', () => {
  it('la settimana parte da lunedì e finisce con domenica', () => {
    expect(indiceGiorno(LUNEDI)).toBe(0)
    expect(indiceGiorno(DOMENICA)).toBe(6)
    expect(trackerSettimana(LUNEDI, 1).map((g) => g.lettera)).toEqual(['L', 'M', 'M', 'G', 'V', 'S', 'D'])
  })

  it('streak N con l\'ultima lezione oggi: oggi e gli N-1 giorni precedenti', () => {
    expect(attivi(MERCOLEDI, 2)).toEqual([false, true, true, false, false, false, false])
    expect(attivi(MERCOLEDI, 1)).toEqual([false, false, true, false, false, false, false])
  })

  it('una streak più lunga della settimana colora solo da lunedì a oggi', () => {
    expect(attivi(MERCOLEDI, 10)).toEqual([true, true, true, false, false, false, false])
    expect(attivi(DOMENICA, 7)).toEqual([true, true, true, true, true, true, true])
  })

  it('di lunedì conta solo lunedì, anche con una streak lunga', () => {
    expect(attivi(LUNEDI, 5)).toEqual([true, false, false, false, false, false, false])
  })

  it('streak 0: nessun giorno colorato', () => {
    expect(attivi(MERCOLEDI, 0)).toEqual([false, false, false, false, false, false, false])
  })

  it('segna il giorno di oggi', () => {
    expect(trackerSettimana(MERCOLEDI, 1).map((g) => g.oggi)).toEqual([false, false, true, false, false, false, false])
  })
})

describe('tempo impiegato', () => {
  it('formatta secondi, minuti e ore', () => {
    expect(formattaDurata(0)).toBe('0s')
    expect(formattaDurata(45_900)).toBe('45s')
    expect(formattaDurata(165_000)).toBe('2m 45s')
    expect(formattaDurata(3_780_000)).toBe('1h 3m')
  })

  it('un valore negativo vale 0; senza inizio o fine non si mostra', () => {
    expect(formattaDurata(-5000)).toBe('0s')
    expect(durata(null, 1000)).toBeNull()
    expect(durata(1000, null)).toBeNull()
    expect(durata(1000, 6000)).toBe('5s')
  })
})
