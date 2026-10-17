import { describe, expect, it } from 'vitest'
import { CODICI_ERRORE } from '../api/ApiError'
import { messaggioErrore } from './messaggiErrore'

describe('messaggioErrore', () => {
  it('traduce ogni codice in un messaggio italiano non vuoto', () => {
    for (const codice of CODICI_ERRORE) {
      expect(messaggioErrore(codice)).not.toBe('')
    }
  })

  it('usa per il login il messaggio unico del backend', () => {
    expect(messaggioErrore('CREDENZIALI_NON_VALIDE')).toBe(
      'Email o password non corretti, oppure troppi tentativi: riprova tra qualche minuto.',
    )
  })

  it('distingue il server in errore dalla connessione assente', () => {
    expect(messaggioErrore('ERRORE_RETE')).not.toBe(messaggioErrore('ERRORE_INTERNO'))
  })

  it('a un codice sconosciuto dà il messaggio generico, senza mostrare il codice', () => {
    const messaggio = messaggioErrore('CODICE_INVENTATO')

    expect(messaggio).toBe(messaggioErrore('ERRORE_INTERNO'))
    expect(messaggio).not.toContain('CODICE_INVENTATO')
  })
})
