import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { messaggioErrore } from '../../common/messaggiErrore'
import { MESSAGGI } from '../../common/validazione'
import { salvaToken } from '../../store/sessione/tokenSalvato'
import { creaStore } from '../../store/store'
import { fetchFinto, profilo, rispostaErrore, rispostaJson } from '../../testUtili'
import type { LezioneAdmin, TappaAdmin } from './tipi'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

function lezione(id: string, ordine: number, modifiche: Partial<LezioneAdmin> = {}): LezioneAdmin {
  return { id, titolo: `Lezione ${id}`, descrizione: 'Descrizione', ordine, tappaId: 't1', numeroDomande: 2, ...modifiche }
}

function tappa(id: string, ordine: number, lezioni: LezioneAdmin[] = []): TappaAdmin {
  return { id, titolo: `Tappa ${id}`, descrizione: `Descrizione ${id}`, ordine, lezioni }
}

const vuota = () => new Response(null, { status: 204 })

type Risposta = () => Response

/** Chiamate a un percorso con un metodo, con il corpo già letto come JSON. */
function chiamate(percorso: string, metodo: string) {
  return fetchFinto.mock.calls
    .filter(([url, init]) => String(url).endsWith(percorso) && (init?.method ?? 'GET') === metodo)
    .map(([, init]) => (init?.body ? JSON.parse(String(init.body)) : undefined))
}

/**
 * Apre /admin/content con un admin. GET /api/admin/stages risponde con gli elenchi dati, uno per lettura (l'ultimo si
 * ripete); le altre chiamate con `risposte[metodo percorso]`, altrimenti 204.
 */
function apri(elenchi: TappaAdmin[][], risposte: Record<string, Risposta> = {}) {
  let letture = 0
  fetchFinto.mockImplementation(async (url, init) => {
    const percorso = String(url).replace(/^https?:\/\/[^/]+/, '')
    const metodo = init?.method ?? 'GET'
    if (percorso === '/api/me') {
      return rispostaJson(200, profilo({ ruolo: 'ADMIN' }))
    }
    if (percorso === '/api/admin/stages' && metodo === 'GET') {
      return rispostaJson(200, elenchi[Math.min(letture++, elenchi.length - 1)])
    }
    return (risposte[`${metodo} ${percorso}`] ?? vuota)()
  })
  salvaToken('token-valido')
  render(
    <Provider store={creaStore()}>
      <MemoryRouter initialEntries={['/admin/content']}>
        <App />
      </MemoryRouter>
    </Provider>,
  )
}

function letture() {
  return chiamate('/api/admin/stages', 'GET').length
}

describe('accordion delle tappe', () => {
  it('all\'avvio è aperta la prima; aprirne un\'altra chiude la precedente', async () => {
    const utente = userEvent.setup()
    apri([[tappa('A', 1, [lezione('a1', 1)]), tappa('B', 2, [lezione('b1', 1)])]])

    const a = await screen.findByRole('button', { name: /Tappa A/, expanded: true })
    const b = screen.getByRole('button', { name: /Tappa B/, expanded: false })
    expect(screen.getByText('L1 · Lezione a1')).toBeInTheDocument()
    expect(screen.queryByText('L1 · Lezione b1')).not.toBeInTheDocument()

    await utente.click(b)
    expect(b).toHaveAttribute('aria-expanded', 'true')
    expect(a).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByText('L1 · Lezione b1')).toBeInTheDocument()
    expect(screen.queryByText('L1 · Lezione a1')).not.toBeInTheDocument()
  })

  it('dopo un riordino resta aperta la stessa tappa (ricordata per id)', async () => {
    const utente = userEvent.setup()
    apri([
      [tappa('A', 1), tappa('B', 2)],
      [tappa('B', 1), tappa('A', 2)],
    ])

    await utente.click(await screen.findByRole('button', { name: /Tappa B/, expanded: false }))
    await utente.click(screen.getByRole('button', { name: 'Sposta su la tappa «Tappa B»' }))

    // nuovo elenco: B è prima, ed è ancora lei quella aperta
    expect(await screen.findByRole('button', { name: /Tappa B/, expanded: true })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Tappa A/, expanded: false })).toBeInTheDocument()
  })
})

describe('etichetta "senza domande"', () => {
  it('compare solo sulle lezioni con zero domande', async () => {
    apri([[tappa('A', 1, [lezione('a1', 1, { numeroDomande: 0 }), lezione('a2', 2, { numeroDomande: 2 })])]])

    const vuota = (await screen.findByText('L1 · Lezione a1')).closest('li')!
    const piena = screen.getByText('L2 · Lezione a2').closest('li')!
    expect(within(vuota).getByText('senza domande')).toBeInTheDocument()
    expect(within(piena).queryByText('senza domande')).not.toBeInTheDocument()
  })
})

describe('riordino', () => {
  it('Su e Giù inviano ordine ∓ 1 e sono disattivati ai bordi', async () => {
    const utente = userEvent.setup()
    apri([[tappa('A', 1, [lezione('a1', 1), lezione('a2', 2)]), tappa('B', 2)]])

    await screen.findByRole('button', { name: /Tappa A/, expanded: true })
    expect(screen.getByRole('button', { name: 'Sposta su la tappa «Tappa A»' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Sposta giù la tappa «Tappa B»' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Sposta su la lezione «Lezione a1»' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Sposta giù la lezione «Lezione a2»' })).toBeDisabled()

    await utente.click(screen.getByRole('button', { name: 'Sposta giù la tappa «Tappa A»' }))
    await vi.waitFor(() => expect(letture()).toBe(2))
    await utente.click(screen.getByRole('button', { name: 'Sposta su la lezione «Lezione a2»' }))
    await vi.waitFor(() => expect(letture()).toBe(3))

    expect(chiamate('/api/admin/stages/A', 'PATCH')).toEqual([{ ordine: 2 }])
    expect(chiamate('/api/admin/lessons/a2', 'PATCH')).toEqual([{ ordine: 1 }])
  })
})

describe('creazione e modifica', () => {
  it('un form vuoto mostra gli errori senza chiamare il backend', async () => {
    const utente = userEvent.setup()
    apri([[]])

    await utente.click(await screen.findByRole('button', { name: '+ Nuova tappa' }))
    await utente.click(screen.getByRole('button', { name: 'Crea tappa' }))

    expect(screen.getByText(MESSAGGI.titoloObbligatorio)).toBeInTheDocument()
    expect(screen.getByText(MESSAGGI.descrizioneObbligatoria)).toBeInTheDocument()
    expect(screen.getByLabelText('Titolo')).toHaveFocus()
    expect(chiamate('/api/admin/stages', 'POST')).toHaveLength(0)
  })

  it('nuova tappa: POST, elenco riletto e la nuova tappa aperta', async () => {
    const utente = userEvent.setup()
    apri([[tappa('A', 1)], [tappa('A', 1), tappa('N', 2)]], {
      'POST /api/admin/stages': () => rispostaJson(201, tappa('N', 2)),
    })

    await utente.click(await screen.findByRole('button', { name: '+ Nuova tappa' }))
    await utente.type(screen.getByLabelText('Titolo'), 'Nuova')
    await utente.type(screen.getByLabelText('Descrizione'), 'Cosa contiene')
    await utente.click(screen.getByRole('button', { name: 'Crea tappa' }))

    expect(await screen.findByRole('button', { name: /Tappa N/, expanded: true })).toBeInTheDocument()
    expect(chiamate('/api/admin/stages', 'POST')).toEqual([{ titolo: 'Nuova', descrizione: 'Cosa contiene' }])
    expect(screen.queryByRole('form', { name: 'Nuova tappa' })).not.toBeInTheDocument()
  })

  it('gli errori dei campi del backend vanno sotto i campi e il form resta aperto', async () => {
    const utente = userEvent.setup()
    apri([[tappa('A', 1)]], {
      'PATCH /api/admin/stages/A': () =>
        rispostaJson(400, {
          status: 400,
          codice: 'VALIDAZIONE',
          errore: '',
          messaggio: '',
          campi: { titolo: 'Errore dal backend' },
          timestamp: '',
        }),
    })

    await utente.click(await screen.findByRole('button', { name: 'Modifica la tappa «Tappa A»' }))
    const form = screen.getByRole('form', { name: 'Modifica la tappa «Tappa A»' })
    expect(within(form).getByLabelText('Titolo')).toHaveValue('Tappa A')
    await utente.click(within(form).getByRole('button', { name: 'Salva' }))

    expect(await within(form).findByText('Errore dal backend')).toBeInTheDocument()
    expect(letture()).toBe(1)
  })

  it('un 404 (eliminata nel frattempo) chiude il form, mostra il messaggio e rilegge', async () => {
    const utente = userEvent.setup()
    apri([[tappa('A', 1, [lezione('a1', 1)])], [tappa('A', 1)]], {
      'PATCH /api/admin/lessons/a1': () => rispostaErrore(404, 'NON_TROVATO'),
    })

    await utente.click(await screen.findByRole('button', { name: 'Modifica la lezione «Lezione a1»' }))
    await utente.click(screen.getByRole('button', { name: 'Salva' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(messaggioErrore('NON_TROVATO'))
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
    await vi.waitFor(() => expect(screen.queryByText('L1 · Lezione a1')).not.toBeInTheDocument())
  })
})

describe('cancellazione', () => {
  it('Annulla non chiama il backend', async () => {
    const utente = userEvent.setup()
    apri([[tappa('A', 1, [lezione('a1', 1), lezione('a2', 2)])]])

    await utente.click(await screen.findByRole('button', { name: 'Elimina la tappa «Tappa A»' }))
    const conferma = screen.getByRole('dialog')
    expect(conferma).toHaveTextContent(
      'Eliminare la tappa «Tappa A»? Verranno eliminate anche le sue 2 lezioni e le loro domande.',
    )
    await utente.click(within(conferma).getByRole('button', { name: 'Annulla' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(chiamate('/api/admin/stages/A', 'DELETE')).toHaveLength(0)
  })

  it('Elimina chiama il backend una volta sola e rilegge; sparita la tappa aperta si apre la prima', async () => {
    const utente = userEvent.setup()
    apri([[tappa('A', 1), tappa('B', 2, [lezione('b1', 1, { numeroDomande: 1 })])], [tappa('A', 1)]])

    await utente.click(await screen.findByRole('button', { name: /Tappa B/, expanded: false }))
    await utente.click(screen.getByRole('button', { name: 'Elimina la lezione «Lezione b1»' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Eliminare la lezione «Lezione b1»? Verranno eliminate anche la sua domanda.')
    await utente.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Annulla' }))

    await utente.click(screen.getByRole('button', { name: 'Elimina la tappa «Tappa B»' }))
    await utente.dblClick(within(screen.getByRole('dialog')).getByRole('button', { name: 'Elimina' }))

    expect(await screen.findByRole('button', { name: /Tappa A/, expanded: true })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Tappa B/ })).not.toBeInTheDocument()
    expect(chiamate('/api/admin/stages/B', 'DELETE')).toHaveLength(1)
    expect(chiamate('/api/admin/lessons/b1', 'DELETE')).toHaveLength(0)
  })
})
