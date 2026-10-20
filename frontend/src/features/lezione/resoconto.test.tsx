import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { messaggioErrore } from '../../common/messaggiErrore'
import { salvaToken } from '../../store/sessione/tokenSalvato'
import { creaStore } from '../../store/store'
import { fetchFinto, profilo, rispostaErrore, rispostaJson } from '../../testUtili'
import type { Resoconto } from './tipi'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

/** Resoconto con le domande nell'ordine dell'admin: giusta, sbagliata, abbinamento con una coppia sbagliata. */
const RESOCONTO: Resoconto = {
  id: 't1',
  esito: 'FALLITO',
  errori: 3,
  iniziatoAt: '2026-10-19T10:00:00Z',
  finitoAt: '2026-10-19T10:02:00Z',
  lezione: { id: 'l1', titolo: 'I muscoli principali' },
  domande: [
    {
      id: 'vf',
      testo: 'Il quadricipite sta davanti alla coscia.',
      tipo: 'VERO_FALSO',
      spiegazione: 'Distende il ginocchio.',
      corretta: true,
      risposta: { id: 'vero', testo: 'Vero' },
      soluzione: { id: 'vero', testo: 'Vero' },
      coppie: [],
    },
    {
      id: 'sm',
      testo: 'Quale muscolo piega il gomito?',
      tipo: 'SCELTA_MULTIPLA',
      spiegazione: 'Il bicipite piega il gomito.',
      corretta: false,
      risposta: { id: 'deltoide', testo: 'Il deltoide' },
      soluzione: { id: 'bicipite', testo: 'Il bicipite' },
      coppie: [],
    },
    {
      id: 'ab',
      testo: 'Abbina ogni muscolo alla sua zona.',
      tipo: 'ABBINAMENTO',
      spiegazione: 'Sapere dove sono i muscoli aiuta.',
      corretta: false,
      risposta: null,
      soluzione: null,
      coppie: [
        {
          sinistro: { id: 's1', testo: 'Pettorale' },
          destro: { id: 'd1', testo: 'Petto' },
          soluzione: { id: 'd1', testo: 'Petto' },
          corretta: true,
        },
        {
          sinistro: { id: 's2', testo: 'Dorsale' },
          destro: { id: 'd3', testo: 'Anca' },
          soluzione: { id: 'd2', testo: 'Schiena' },
          corretta: false,
        },
      ],
    },
  ],
}

/** Apre /attempts/t1; GET del resoconto risponde con le risposte date, una per lettura (l'ultima si ripete). */
function apri(risposte: Array<() => Response>) {
  let letture = 0
  fetchFinto.mockImplementation(async (url) => {
    const percorso = String(url).replace(/^https?:\/\/[^/]+/, '')
    if (percorso === '/api/me') {
      return rispostaJson(200, profilo())
    }
    if (percorso === '/api/attempts/t1') {
      return risposte[Math.min(letture++, risposte.length - 1)]()
    }
    if (percorso === '/api/path/stages') {
      return rispostaJson(200, [])
    }
    return rispostaErrore(500, 'ERRORE_INTERNO')
  })
  salvaToken('token-valido')
  render(
    <Provider store={creaStore()}>
      <MemoryRouter initialEntries={['/attempts/t1']}>
        <App />
      </MemoryRouter>
    </Provider>,
  )
}

const letture = () => fetchFinto.mock.calls.filter(([url]) => String(url).endsWith('/api/attempts/t1')).length

describe('resoconto del tentativo', () => {
  it('intestazione con esito, lezione, errori e numero di domande; domande nell\'ordine ricevuto', async () => {
    apri([() => rispostaJson(200, RESOCONTO)])

    expect(await screen.findByText('I muscoli principali')).toBeInTheDocument()
    expect(screen.getByText(/Non superato/)).toBeInTheDocument()
    expect(screen.getByText('Errori 3/3 · 3 domande')).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([
      'Il quadricipite sta davanti alla coscia.',
      'Quale muscolo piega il gomito?',
      'Abbina ogni muscolo alla sua zona.',
    ])
  })

  it('risposta giusta: solo "La tua risposta"; sbagliata: anche "Risposta corretta"; sempre la spiegazione', async () => {
    apri([() => rispostaJson(200, RESOCONTO)])

    const giusta = (await screen.findByRole('heading', { name: 'Il quadricipite sta davanti alla coscia.' })).parentElement!
    expect(within(giusta).getByText('✓ Corretta')).toBeInTheDocument()
    expect(giusta).toHaveTextContent('La tua risposta: Vero')
    expect(giusta).not.toHaveTextContent('Risposta corretta')
    expect(giusta).toHaveTextContent('Spiegazione: Distende il ginocchio.')

    const sbagliata = screen.getByRole('heading', { name: 'Quale muscolo piega il gomito?' }).parentElement!
    expect(within(sbagliata).getByText('✗ Da rivedere')).toBeInTheDocument()
    expect(sbagliata).toHaveTextContent('La tua risposta (errata): Il deltoide')
    expect(sbagliata).toHaveTextContent('Risposta corretta: Il bicipite')
  })

  it('abbinamento: ogni coppia con ✓ o ✗ e il destro giusto per quelle sbagliate', async () => {
    apri([() => rispostaJson(200, RESOCONTO)])

    const abbinamento = (await screen.findByRole('heading', { name: 'Abbina ogni muscolo alla sua zona.' }))
      .parentElement!
    const coppie = within(abbinamento).getAllByRole('listitem')
    expect(coppie[0]).toHaveTextContent('✓ Pettorale → Petto, giusta')
    expect(coppie[1]).toHaveTextContent('✗ Dorsale → Anca, sbagliata (giusto: Schiena)')
    expect(abbinamento).not.toHaveTextContent('La tua risposta')
  })

  it('404 (tentativo inesistente o altrui): messaggio dedicato e "Torna alla mappa", senza "Riprova"', async () => {
    const utente = userEvent.setup()
    apri([() => rispostaErrore(404, 'NON_TROVATO')])

    expect(await screen.findByText('Questo resoconto non è disponibile.')).toBeInTheDocument()
    expect(screen.queryByText(messaggioErrore('NON_TROVATO'))).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Riprova' })).not.toBeInTheDocument()

    await utente.click(screen.getByRole('button', { name: 'Torna alla mappa' }))
    expect(await screen.findByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
  })

  it('500 o rete: messaggio d\'errore normale (non quello del 404) e "Riprova" che rilegge', async () => {
    const utente = userEvent.setup()
    apri([() => rispostaErrore(500, 'ERRORE_INTERNO'), () => rispostaJson(200, RESOCONTO)])

    expect(await screen.findByText(messaggioErrore('ERRORE_INTERNO'))).toBeInTheDocument()
    expect(screen.queryByText('Questo resoconto non è disponibile.')).not.toBeInTheDocument()

    await utente.click(screen.getByRole('button', { name: 'Riprova' }))

    expect(await screen.findByText('I muscoli principali')).toBeInTheDocument()
    expect(letture()).toBe(2)
  })

  it('errore di rete: stesso trattamento del 500', async () => {
    apri([
      () => {
        throw new TypeError('rete')
      },
    ])

    expect(await screen.findByText(messaggioErrore('ERRORE_RETE'))).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Riprova' })).toBeInTheDocument()
  })
})
