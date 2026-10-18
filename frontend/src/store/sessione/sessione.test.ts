import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { creaStore } from '../store'
import { caricaProfilo, impostaToken, logout } from './sessioneSlice'
import { fetchFinto, profilo, rispostaErrore, rispostaJson, thunkCheFallisce } from '../../testUtili'
import { leggiToken, salvaToken } from './tokenSalvato'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

/** Store con un token salvato e il profilo già caricato. */
async function storeConProfilo() {
  salvaToken('token-valido')
  const store = creaStore()
  fetchFinto.mockResolvedValue(rispostaJson(200, profilo()))
  await store.dispatch(caricaProfilo())
  return store
}

describe('sessione', () => {
  it('all\'avvio prende il token salvato in localStorage', () => {
    salvaToken('token-salvato')

    expect(creaStore().getState().sessione.token).toBe('token-salvato')
  })

  it('impostaToken salva il token anche in localStorage', () => {
    const store = creaStore()

    store.dispatch(impostaToken('nuovo-token'))

    expect(store.getState().sessione.token).toBe('nuovo-token')
    expect(leggiToken()).toBe('nuovo-token')
  })

  it('caricaProfilo chiama /api/me con il token e salva il profilo', async () => {
    const store = await storeConProfilo()

    const [, init] = fetchFinto.mock.calls[0]
    expect(init?.headers).toEqual({ Authorization: 'Bearer token-valido' })
    expect(store.getState().sessione.caricamento).toBe('riuscito')
    expect(store.getState().sessione.utente).toEqual(profilo())
  })

  it('logout cancella il token salvato e tutto lo stato', async () => {
    const store = await storeConProfilo()

    store.dispatch(logout())

    expect(leggiToken()).toBeNull()
    expect(store.getState().sessione).toEqual({ token: null, utente: null, caricamento: 'inattivo', errore: null })
  })

  it('401 NON_AUTENTICATO su /api/me fa logout', async () => {
    salvaToken('token-scaduto')
    const store = creaStore()
    fetchFinto.mockResolvedValue(rispostaErrore(401, 'NON_AUTENTICATO'))

    await store.dispatch(caricaProfilo())

    expect(store.getState().sessione.token).toBeNull()
    expect(leggiToken()).toBeNull()
  })

  it('401 NON_AUTENTICATO da qualunque altro thunk fa logout', async () => {
    const store = await storeConProfilo()

    await store.dispatch(thunkCheFallisce('NON_AUTENTICATO'))

    expect(store.getState().sessione.token).toBeNull()
    expect(store.getState().sessione.utente).toBeNull()
    expect(leggiToken()).toBeNull()
  })

  it('403 EMAIL_NON_VERIFICATA segna l\'email come non verificata e non fa logout', async () => {
    const store = await storeConProfilo()

    await store.dispatch(thunkCheFallisce('EMAIL_NON_VERIFICATA'))

    expect(store.getState().sessione.utente?.emailVerificata).toBe(false)
    expect(store.getState().sessione.token).toBe('token-valido')
  })

  it('altri errori non toccano la sessione', async () => {
    const store = await storeConProfilo()

    await store.dispatch(thunkCheFallisce('ACCESSO_NEGATO'))

    expect(store.getState().sessione.token).toBe('token-valido')
    expect(store.getState().sessione.utente?.emailVerificata).toBe(true)
  })

  it('errore di rete su /api/me: il token resta e si registra l\'errore', async () => {
    salvaToken('token-valido')
    const store = creaStore()
    fetchFinto.mockRejectedValue(new TypeError('Failed to fetch'))

    await store.dispatch(caricaProfilo())

    expect(store.getState().sessione.caricamento).toBe('fallito')
    expect(store.getState().sessione.errore?.codice).toBe('ERRORE_RETE')
    expect(store.getState().sessione.token).toBe('token-valido')
    expect(leggiToken()).toBe('token-valido')
  })
})
