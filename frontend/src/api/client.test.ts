import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './ApiError'
import { richiesta } from './client'

const fetchFinto = vi.fn<typeof fetch>()

beforeEach(() => {
  vi.stubEnv('VITE_API_URL', 'http://api.test')
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

function rispostaJson(status: number, corpo: unknown): Response {
  return new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } })
}

/** Esegue la richiesta e restituisce l'errore lanciato (il test fallisce se non ne lancia). */
async function erroreDi(promessa: Promise<unknown>): Promise<ApiError> {
  try {
    await promessa
  } catch (errore) {
    expect(errore).toBeInstanceOf(ApiError)
    return errore as ApiError
  }
  throw new Error('La richiesta doveva fallire')
}

describe('richiesta', () => {
  it('antepone VITE_API_URL e aggiunge il token nell\'header Authorization', async () => {
    fetchFinto.mockResolvedValue(rispostaJson(200, { nome: 'Mario' }))

    const risposta = await richiesta<{ nome: string }>('/api/me', { token: 'abc' })

    expect(risposta).toEqual({ nome: 'Mario' })
    const [url, init] = fetchFinto.mock.calls[0]
    expect(url).toBe('http://api.test/api/me')
    expect(init?.headers).toEqual({ Authorization: 'Bearer abc' })
  })

  it('senza token non invia l\'header Authorization', async () => {
    fetchFinto.mockResolvedValue(rispostaJson(200, {}))

    await richiesta('/api/auth/login', { metodo: 'POST', corpo: { email: 'a@b.it' } })

    const [, init] = fetchFinto.mock.calls[0]
    expect(init?.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(init?.body).toBe('{"email":"a@b.it"}')
  })

  it('con FormData non imposta Content-Type (lo fa il browser con il boundary)', async () => {
    fetchFinto.mockResolvedValue(rispostaJson(200, {}))
    const form = new FormData()
    form.append('file', new Blob(['x']), 'foto.png')

    await richiesta('/api/me/avatar', { metodo: 'PATCH', corpo: form, token: 'abc' })

    const [, init] = fetchFinto.mock.calls[0]
    expect(init?.headers).toEqual({ Authorization: 'Bearer abc' })
    expect(init?.body).toBe(form)
  })

  it('una risposta 204 senza corpo restituisce undefined', async () => {
    fetchFinto.mockResolvedValue(new Response(null, { status: 204 }))

    await expect(richiesta('/api/me/verification', { metodo: 'POST' })).resolves.toBeUndefined()
  })

  it('trasforma l\'errore del backend in ApiError con codice, messaggio e campi', async () => {
    fetchFinto.mockResolvedValue(
      rispostaJson(400, {
        status: 400,
        codice: 'VALIDAZIONE',
        errore: 'Bad Request',
        messaggio: 'Uno o più campi non sono validi',
        campi: { email: "L'email è obbligatoria" },
        timestamp: '2026-10-17T10:00:00Z',
      }),
    )

    const errore = await erroreDi(richiesta('/api/auth/register', { metodo: 'POST', corpo: {} }))

    expect(errore.dati()).toEqual({
      status: 400,
      codice: 'VALIDAZIONE',
      messaggio: 'Uno o più campi non sono validi',
      campi: { email: "L'email è obbligatoria" },
    })
  })

  it('un errore non nel formato del backend diventa ERRORE_INTERNO', async () => {
    fetchFinto.mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502 }))

    const errore = await erroreDi(richiesta('/api/me'))

    expect(errore.status).toBe(502)
    expect(errore.codice).toBe('ERRORE_INTERNO')
  })

  it('se il server non risponde lancia ERRORE_RETE con status 0', async () => {
    fetchFinto.mockRejectedValue(new TypeError('Failed to fetch'))

    const errore = await erroreDi(richiesta('/api/me'))

    expect(errore.status).toBe(0)
    expect(errore.codice).toBe('ERRORE_RETE')
  })
})
