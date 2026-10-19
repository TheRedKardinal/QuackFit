import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { messaggioErrore } from '../../common/messaggiErrore'
import { salvaToken } from '../../store/sessione/tokenSalvato'
import { creaStore } from '../../store/store'
import { fetchFinto, profilo, rispostaErrore, rispostaJson } from '../../testUtili'
import type { LezionePercorso, StatoLezione, TappaPercorso } from './tipi'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

function lezione(id: string, ordine: number, stato: StatoLezione): LezionePercorso {
  return { id, titolo: `Lezione ${id}`, descrizione: 'Descrizione', ordine, stato }
}

function tappa(id: string, ordine: number, lezioni: LezionePercorso[]): TappaPercorso {
  return { id, titolo: `Tappa ${id}`, descrizione: `Descrizione ${id}`, ordine, lezioni }
}

const MAPPA = [
  tappa('A', 1, [lezione('a1', 1, 'COMPLETATA'), lezione('a2', 2, 'SBLOCCATA'), lezione('a3', 3, 'BLOCCATA')]),
  tappa('B', 2, [lezione('b1', 1, 'BLOCCATA')]),
]

type Risposta = () => Response

/**
 * Apre la mappa ("/") con un utente verificato. GET /api/path/stages risponde con le mappe date, una per lettura
 * (l'ultima si ripete), oppure con `erroreMappa`; POST /api/attempts con `avvio`; il contenuto di una lezione con una
 * domanda sola (per vedere che l'avvio porta alla lezione).
 */
function apri(mappe: TappaPercorso[][], avvio?: Risposta, erroreMappa?: Risposta) {
  let letture = 0
  fetchFinto.mockImplementation(async (url, init) => {
    const percorso = String(url).replace(/^https?:\/\/[^/]+/, '')
    const metodo = init?.method ?? 'GET'
    if (percorso === '/api/me') {
      return rispostaJson(200, profilo())
    }
    if (percorso === '/api/path/stages') {
      letture++
      return erroreMappa ? erroreMappa() : rispostaJson(200, mappe[Math.min(letture - 1, mappe.length - 1)])
    }
    if (percorso.startsWith('/api/path/lessons/')) {
      const domanda = { id: 'd1', testo: 'Prima domanda', tipo: 'VERO_FALSO', opzioni: [], sinistra: [], destra: [] }
      return rispostaJson(200, { id: 'a2', titolo: 'Lezione a2', descrizione: 'D', domande: [domanda] })
    }
    if (percorso === '/api/attempts' && metodo === 'POST' && avvio) {
      return avvio()
    }
    return rispostaErrore(500, 'ERRORE_INTERNO')
  })
  salvaToken('token-valido')
  render(
    <Provider store={creaStore()}>
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    </Provider>,
  )
}

function letture() {
  return fetchFinto.mock.calls.filter(([url]) => String(url).endsWith('/api/path/stages')).length
}

function avvii() {
  return fetchFinto.mock.calls
    .filter(([url, init]) => String(url).endsWith('/api/attempts') && init?.method === 'POST')
    .map(([, init]) => JSON.parse(String(init?.body)))
}

describe('mappa del percorso', () => {
  it('mostra tutte le tappe con i nodi nei tre stati, scritti anche a parole', async () => {
    apri([MAPPA])

    expect(await screen.findByRole('heading', { name: 'Tappa A' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Tappa B' })).toBeInTheDocument()
    expect(screen.getByText('1/3 completate')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Lezione 1, Lezione a1: completata' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Lezione 2, Lezione a2: da fare' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Lezione 3, Lezione a3: bloccata' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Lezione 1, Lezione b1: bloccata' })).toBeDisabled()
  })

  it('il tocco su una lezione la avvia e porta alla lezione', async () => {
    const utente = userEvent.setup()
    apri([MAPPA], () => rispostaJson(201, { tentativoId: 'tentativo-1' }))

    await utente.click(await screen.findByRole('button', { name: /Lezione a2: da fare/ }))

    expect(avvii()).toEqual([{ lezioneId: 'a2' }])
    expect(await screen.findByRole('heading', { name: 'Prima domanda' })).toBeInTheDocument()
  })

  it('si può rifare una lezione completata', async () => {
    const utente = userEvent.setup()
    apri([MAPPA], () => rispostaJson(201, { tentativoId: 'tentativo-1' }))

    await utente.click(await screen.findByRole('button', { name: /Lezione a1: completata/ }))

    expect(avvii()).toEqual([{ lezioneId: 'a1' }])
  })

  it('404 all\'avvio: riquadro "Lezione in arrivo" e mappa ricaricata', async () => {
    const utente = userEvent.setup()
    apri([MAPPA], () => rispostaErrore(404, 'NON_TROVATO'))

    await utente.click(await screen.findByRole('button', { name: /Lezione a2: da fare/ }))

    expect(await screen.findByText('Lezione in arrivo, torna a trovarci presto')).toBeInTheDocument()
    expect(screen.queryByText(messaggioErrore('NON_TROVATO'))).not.toBeInTheDocument()
    await vi.waitFor(() => expect(letture()).toBe(2))
    expect(screen.getByRole('heading', { name: 'Tappa A' })).toBeInTheDocument()
  })

  it('403 LEZIONE_BLOCCATA (mappa non aggiornata): messaggio e mappa ricaricata con gli stati nuovi', async () => {
    const utente = userEvent.setup()
    const aggiornata = [tappa('A', 1, [lezione('a1', 1, 'COMPLETATA'), lezione('a2', 2, 'BLOCCATA')])]
    apri([MAPPA, aggiornata], () => rispostaErrore(403, 'LEZIONE_BLOCCATA'))

    await utente.click(await screen.findByRole('button', { name: /Lezione a2: da fare/ }))

    expect(await screen.findByText(messaggioErrore('LEZIONE_BLOCCATA'))).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /Lezione a2: bloccata/ })).toBeDisabled()
    expect(letture()).toBe(2)
  })

  it('un doppio tocco avvia una sola volta', async () => {
    const utente = userEvent.setup()
    let rispondi: (risposta: Response) => void = () => {}
    apri([MAPPA], () => new Promise<Response>((r) => (rispondi = r)) as unknown as Response)

    const nodo = await screen.findByRole('button', { name: /Lezione a2: da fare/ })
    await utente.dblClick(nodo)

    expect(avvii()).toHaveLength(1)
    expect(nodo).toBeDisabled()
    rispondi(rispostaJson(201, { tentativoId: 'tentativo-1' }))
  })

  it('errore di caricamento: messaggio e "Riprova" che rilegge', async () => {
    const utente = userEvent.setup()
    apri([MAPPA], undefined, () => rispostaErrore(500, 'ERRORE_INTERNO'))

    expect(await screen.findByText(messaggioErrore('ERRORE_INTERNO'))).toBeInTheDocument()
    await utente.click(screen.getByRole('button', { name: 'Riprova' }))

    await vi.waitFor(() => expect(letture()).toBe(2))
  })

  it('senza tappe: percorso in preparazione', async () => {
    apri([[]])

    expect(await screen.findByText('Il percorso è in preparazione: torna a trovarci presto.')).toBeInTheDocument()
  })
})
