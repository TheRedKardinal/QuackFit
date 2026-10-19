import { describe, expect, it } from 'vitest'
import {
  abbinamentoVuoto,
  completa,
  coppieDaInviare,
  numeri,
  quanteCoppie,
  toccaDestro,
  toccaSinistro,
  type StatoAbbinamento,
} from './abbinamento'

const SINISTRI = ['s1', 's2', 's3']

/** Collega un sinistro a un destro come farebbe l'utente: due tocchi. */
function collega(stato: StatoAbbinamento, sinistro: string, destro: string) {
  return toccaDestro(toccaSinistro(stato, sinistro), destro)
}

describe('collegamenti dell\'abbinamento', () => {
  it('un sinistro e poi un destro diventano una coppia', () => {
    const stato = collega(abbinamentoVuoto, 's1', 'd2')

    expect(stato.coppie).toEqual({ s1: 'd2' })
    expect(stato.selezionato).toBeNull()
    expect(quanteCoppie(stato)).toBe(1)
  })

  it('un destro senza sinistro selezionato non fa nulla', () => {
    expect(toccaDestro(abbinamentoVuoto, 'd1')).toEqual(abbinamentoVuoto)
  })

  it('toccare di nuovo il sinistro selezionato lo deseleziona', () => {
    const stato = toccaSinistro(toccaSinistro(abbinamentoVuoto, 's1'), 's1')

    expect(stato.selezionato).toBeNull()
  })

  it('riselezionare un sinistro collegato scioglie la sua coppia', () => {
    let stato = collega(abbinamentoVuoto, 's1', 'd1')
    stato = toccaSinistro(stato, 's1')

    expect(stato.coppie).toEqual({})
    expect(stato.selezionato).toBe('s1')

    stato = toccaDestro(stato, 'd3')
    expect(stato.coppie).toEqual({ s1: 'd3' })
  })

  it('un destro già usato si sposta sul nuovo sinistro e il precedente resta senza coppia', () => {
    let stato = collega(abbinamentoVuoto, 's1', 'd1')
    stato = collega(stato, 's2', 'd1')

    expect(stato.coppie).toEqual({ s2: 'd1' })
    // nessun destro con due sinistri
    expect(new Set(Object.values(stato.coppie)).size).toBe(Object.keys(stato.coppie).length)
  })

  it('numeri sempre 1, 2, 3… nell\'ordine dei sinistri, senza duplicati né buchi dopo una riassegnazione', () => {
    let stato = collega(abbinamentoVuoto, 's3', 'd1')
    stato = collega(stato, 's1', 'd2')
    expect(numeri(stato, SINISTRI)).toEqual({ s1: 1, d2: 1, s3: 2, d1: 2 })

    // d1 passa da s3 a s2: s3 resta senza coppia e i numeri si ricompattano
    stato = collega(stato, 's2', 'd1')
    expect(numeri(stato, SINISTRI)).toEqual({ s1: 1, d2: 1, s2: 2, d1: 2 })

    // sciogliere s1 fa scalare la coppia di s2 a 1
    stato = toccaSinistro(stato, 's1')
    expect(numeri(stato, SINISTRI)).toEqual({ s2: 1, d1: 1 })
  })

  it('completa solo quando tutti i sinistri sono collegati', () => {
    let stato = collega(abbinamentoVuoto, 's1', 'd1')
    stato = collega(stato, 's2', 'd2')
    expect(completa(stato, SINISTRI)).toBe(false)

    stato = collega(stato, 's3', 'd3')
    expect(completa(stato, SINISTRI)).toBe(true)

    // spostare un destro rompe la completezza
    stato = collega(stato, 's1', 'd2')
    expect(completa(stato, SINISTRI)).toBe(false)
    expect(quanteCoppie(stato)).toBe(2)
  })

  it('le coppie da inviare seguono l\'ordine dei sinistri', () => {
    let stato = collega(abbinamentoVuoto, 's3', 'd1')
    stato = collega(stato, 's1', 'd3')
    stato = collega(stato, 's2', 'd2')

    expect(coppieDaInviare(stato, SINISTRI)).toEqual([
      { sinistroId: 's1', destroId: 'd3' },
      { sinistroId: 's2', destroId: 'd2' },
      { sinistroId: 's3', destroId: 'd1' },
    ])
  })
})
