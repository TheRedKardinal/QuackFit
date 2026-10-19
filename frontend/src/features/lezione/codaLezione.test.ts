import { describe, expect, it } from 'vitest'
import { avanza, codaIniziale, domandaCorrente, finita, risolte } from './codaLezione'

describe('coda della lezione', () => {
  it('propone le domande nell\'ordine in cui arrivano', () => {
    let coda = codaIniziale(['a', 'b', 'c'])
    expect(domandaCorrente(coda)).toBe('a')

    coda = avanza(coda, true)
    expect(domandaCorrente(coda)).toBe('b')
  })

  it('una sbagliata torna a fine giro, una giusta non torna più', () => {
    let coda = codaIniziale(['a', 'b', 'c'])
    coda = avanza(coda, false) // a sbagliata
    coda = avanza(coda, true) // b giusta
    expect(domandaCorrente(coda)).toBe('c')

    coda = avanza(coda, true) // c giusta: finito il giro, si ripropone a
    expect(domandaCorrente(coda)).toBe('a')
    expect(finita(coda)).toBe(false)

    coda = avanza(coda, true)
    expect(finita(coda)).toBe(true)
    expect(domandaCorrente(coda)).toBeNull()
  })

  it('una sbagliata di nuovo torna ancora, finché non è risolta', () => {
    let coda = codaIniziale(['a'])
    coda = avanza(coda, false)
    expect(domandaCorrente(coda)).toBe('a')
    coda = avanza(coda, false)
    expect(domandaCorrente(coda)).toBe('a')
    coda = avanza(coda, true)
    expect(finita(coda)).toBe(true)
  })

  it('più sbagliate tornano nell\'ordine in cui sono state sbagliate', () => {
    let coda = codaIniziale(['a', 'b', 'c'])
    coda = avanza(coda, true)
    coda = avanza(coda, false) // b
    coda = avanza(coda, false) // c
    expect(coda.daFare).toEqual(['b', 'c'])
  })

  it('conta le domande risolte per la barra di avanzamento', () => {
    let coda = codaIniziale(['a', 'b', 'c'])
    expect(risolte(coda, 3)).toBe(0)
    coda = avanza(coda, true)
    coda = avanza(coda, false)
    expect(risolte(coda, 3)).toBe(1)
  })

  it('su una coda finita avanzare non cambia nulla', () => {
    const vuota = codaIniziale([])
    expect(finita(vuota)).toBe(true)
    expect(avanza(vuota, true)).toEqual(vuota)
  })
})
