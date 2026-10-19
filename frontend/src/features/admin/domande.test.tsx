import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { MESSAGGI } from '../../common/validazione'
import { salvaToken } from '../../store/sessione/tokenSalvato'
import { creaStore } from '../../store/store'
import { fetchFinto, profilo, rispostaErrore, rispostaJson } from '../../testUtili'
import type { DomandaAdmin, LezioneDettaglioAdmin, TappaAdmin } from './tipi'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

const TAPPE: TappaAdmin[] = [
  {
    id: 't1',
    titolo: 'Tappa uno',
    descrizione: 'Prima',
    ordine: 1,
    lezioni: [
      { id: 'l1', titolo: 'Addominali', descrizione: 'D', ordine: 1, tappaId: 't1', numeroDomande: 2 },
      { id: 'l2', titolo: 'Sudore', descrizione: 'D', ordine: 2, tappaId: 't1', numeroDomande: 0 },
    ],
  },
  { id: 't2', titolo: 'Tappa due', descrizione: 'Seconda', ordine: 2, lezioni: [] },
]

const VERO_FALSO: DomandaAdmin = {
  id: 'd1',
  testo: 'Gli addominali bruciano la pancia?',
  spiegazione: 'No, serve il deficit',
  tipo: 'VERO_FALSO',
  ordine: 1,
  opzioni: [
    { id: 'o-vero', testo: 'Vero', corretta: false },
    { id: 'o-falso', testo: 'Falso', corretta: true },
  ],
  coppie: [],
}

const MULTIPLA: DomandaAdmin = {
  id: 'd2',
  testo: 'Cosa riduce il grasso?',
  spiegazione: 'Il deficit calorico',
  tipo: 'SCELTA_MULTIPLA',
  ordine: 2,
  opzioni: [
    { id: 'o1', testo: 'Deficit calorico', corretta: true },
    { id: 'o2', testo: 'Sudare', corretta: false },
  ],
  coppie: [],
}

const ABBINAMENTO: DomandaAdmin = {
  id: 'd3',
  testo: 'Abbina',
  spiegazione: 'Così',
  tipo: 'ABBINAMENTO',
  ordine: 3,
  opzioni: [],
  coppie: [
    { sinistro: { id: 's1', testo: 'Bicipite' }, destro: { id: 'r1', testo: 'Braccio' } },
    { sinistro: { id: 's2', testo: 'Quadricipite' }, destro: { id: 'r2', testo: 'Coscia' } },
  ],
}

function dettaglio(id: string, domande: DomandaAdmin[]): LezioneDettaglioAdmin {
  const lezione = TAPPE[0].lezioni.find((l) => l.id === id)!
  return { id, titolo: lezione.titolo, descrizione: 'D', ordine: lezione.ordine, tappaId: 't1', domande }
}

type Risposta = () => Response
const vuota = () => new Response(null, { status: 204 })

function chiamate(percorso: string, metodo: string) {
  return fetchFinto.mock.calls
    .filter(([url, init]) => String(url).endsWith(percorso) && (init?.method ?? 'GET') === metodo)
    .map(([, init]) => (init?.body ? JSON.parse(String(init.body)) : undefined))
}

/**
 * Apre /admin/content con un admin. GET della lezione: le risposte date in ordine (l'ultima si ripete);
 * le altre chiamate con `risposte[metodo percorso]`, altrimenti 204.
 */
function apri(lezioni: Record<string, Risposta[]>, risposte: Record<string, Risposta> = {}) {
  const letture: Record<string, number> = {}
  fetchFinto.mockImplementation(async (url, init) => {
    const percorso = String(url).replace(/^https?:\/\/[^/]+/, '')
    const metodo = init?.method ?? 'GET'
    if (percorso === '/api/me') {
      return rispostaJson(200, profilo({ ruolo: 'ADMIN' }))
    }
    if (percorso === '/api/admin/stages' && metodo === 'GET') {
      return rispostaJson(200, TAPPE)
    }
    const lezione = /^\/api\/admin\/lessons\/([^/]+)$/.exec(percorso)
    if (lezione && metodo === 'GET') {
      const elenco = lezioni[lezione[1]] ?? [() => rispostaErrore(404, 'NON_TROVATO')]
      const n = letture[lezione[1]] ?? 0
      letture[lezione[1]] = n + 1
      return elenco[Math.min(n, elenco.length - 1)]()
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

const json = (corpo: unknown) => () => rispostaJson(200, corpo)

function errore400(campi: Record<string, string>): Risposta {
  return () =>
    rispostaJson(400, { status: 400, codice: 'VALIDAZIONE', errore: '', messaggio: '', campi, timestamp: '' })
}

async function scegli(utente: ReturnType<typeof userEvent.setup>, titolo: string) {
  await utente.click(await screen.findByRole('button', { name: `Domande della lezione «${titolo}»` }))
}

function editor() {
  return screen.getByRole('region', { name: 'Editor delle domande' })
}

describe('lezione scelta', () => {
  it('all\'avvio nessuna lezione; la scelta apre le sue domande con le soluzioni', async () => {
    const utente = userEvent.setup()
    apri({ l1: [json(dettaglio('l1', [VERO_FALSO, MULTIPLA, ABBINAMENTO]))] })

    expect(await screen.findByText('Scegli una lezione con "Domande" per gestirne le domande.')).toBeInTheDocument()
    expect(chiamate('/api/admin/lessons/l1', 'GET')).toHaveLength(0)

    await scegli(utente, 'Addominali')
    expect(await within(editor()).findByRole('heading', { name: 'Addominali' })).toBeInTheDocument()
    const vf = within(editor()).getByRole('article', { name: 'Domanda 1' })
    expect(within(vf).getByText('Falso').closest('li')).toHaveTextContent('Corretta')
    expect(within(vf).getByText('Vero').closest('li')).not.toHaveTextContent('Corretta')
    const abbinamento = within(editor()).getByRole('article', { name: 'Domanda 3' })
    expect(within(abbinamento).getByText('Bicipite').closest('li')).toHaveTextContent('Braccio')
    // il conteggio a sinistra viene dalle domande caricate
    expect(screen.getByRole('button', { name: 'Domande della lezione «Addominali»' })).toHaveTextContent('Domande (3)')
  })

  it('un 404 sul caricamento svuota l\'editor con un messaggio', async () => {
    const utente = userEvent.setup()
    apri({ l2: [() => rispostaErrore(404, 'NON_TROVATO')] })

    await scegli(utente, 'Sudore')

    expect(await within(editor()).findByText("Questa lezione non esiste più: scegline un'altra.")).toBeInTheDocument()
    expect(within(editor()).queryByRole('button', { name: '+ Nuova domanda' })).not.toBeInTheDocument()
  })

  it('su mobile si vede una colonna sola; "← Tappe" azzera la scelta e la tappa aperta resta la stessa', async () => {
    const utente = userEvent.setup()
    apri({ l1: [json(dettaglio('l1', [VERO_FALSO]))] })

    const colonnaElenco = (await screen.findByRole('heading', { name: 'Gestione contenuti' })).closest('.col-12')!
    const colonnaEditor = () => editor().closest('.col-12')!
    expect(colonnaElenco).not.toHaveClass('d-none')
    expect(colonnaEditor()).toHaveClass('d-none', 'd-lg-block')

    await scegli(utente, 'Addominali')
    await within(editor()).findByRole('heading', { name: 'Addominali' })
    // entrambe nel DOM: le classi decidono cosa si vede (da lg in su tutte e due)
    expect(colonnaElenco).toHaveClass('d-none', 'd-lg-block')
    expect(colonnaEditor()).not.toHaveClass('d-none')

    await utente.click(within(editor()).getByRole('button', { name: '← Tappe' }))
    expect(colonnaElenco).not.toHaveClass('d-none')
    expect(colonnaEditor()).toHaveClass('d-none', 'd-lg-block')
    expect(screen.getByRole('button', { name: /Tappa uno/, expanded: true })).toBeInTheDocument()
  })
})

describe('nuova domanda', () => {
  it('vero o falso: un form vuoto mostra gli errori; poi POST con i soli campi del tipo e la lezione resta scelta', async () => {
    const utente = userEvent.setup()
    apri(
      { l2: [json(dettaglio('l2', [])), json(dettaglio('l2', [VERO_FALSO]))] },
      { 'POST /api/admin/lessons/l2/questions': () => rispostaJson(201, VERO_FALSO) },
    )
    await scegli(utente, 'Sudore')
    await utente.click(await within(editor()).findByRole('button', { name: '+ Nuova domanda' }))

    const form = within(editor()).getByRole('form', { name: 'Nuova domanda' })
    expect(within(form).getByLabelText('Testo della domanda')).toHaveFocus()
    await utente.click(within(form).getByRole('button', { name: 'Crea domanda' }))
    expect(within(form).getByText(MESSAGGI.testoObbligatorio)).toBeInTheDocument()
    expect(within(form).getByText(MESSAGGI.spiegazioneObbligatoria)).toBeInTheDocument()
    expect(within(form).getByText(MESSAGGI.rispostaVero)).toBeInTheDocument()

    await utente.type(within(form).getByLabelText('Testo della domanda'), 'Domanda')
    await utente.type(within(form).getByLabelText('Spiegazione (mostrata dopo la risposta)'), 'Perché')
    await utente.click(within(form).getByLabelText('Falso'))
    await utente.click(within(form).getByRole('button', { name: 'Crea domanda' }))

    expect(await within(editor()).findByRole('article', { name: 'Domanda 1' })).toBeInTheDocument()
    expect(chiamate('/api/admin/lessons/l2/questions', 'POST')).toEqual([
      { testo: 'Domanda', spiegazione: 'Perché', tipo: 'VERO_FALSO', rispostaVero: false },
    ])
    expect(within(editor()).getByRole('heading', { name: 'Sudore' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Domande della lezione «Sudore»' })).toHaveTextContent('Domande (1)')
  })

  it('scelta multipla: da 2 a 6 opzioni, una sola corretta, e il body con le opzioni', async () => {
    const utente = userEvent.setup()
    apri({ l2: [json(dettaglio('l2', []))] }, { 'POST /api/admin/lessons/l2/questions': () => rispostaJson(201, MULTIPLA) })
    await scegli(utente, 'Sudore')
    await utente.click(await within(editor()).findByRole('button', { name: '+ Nuova domanda' }))
    const form = within(editor()).getByRole('form', { name: 'Nuova domanda' })
    await utente.click(within(form).getByLabelText('Scelta multipla'))

    // si parte da 2: non si toglie; si arriva a 6: non si aggiunge
    expect(within(form).getByRole('button', { name: "Togli l'opzione 1" })).toBeDisabled()
    const aggiungi = within(form).getByRole('button', { name: '+ Aggiungi opzione' })
    for (let i = 0; i < 4; i++) {
      await utente.click(aggiungi)
    }
    expect(within(form).getAllByLabelText(/^Opzione \d$/)).toHaveLength(6)
    expect(aggiungi).toBeDisabled()
    await utente.click(within(form).getByRole('button', { name: "Togli l'opzione 6" }))
    await utente.click(within(form).getByRole('button', { name: "Togli l'opzione 5" }))
    await utente.click(within(form).getByRole('button', { name: "Togli l'opzione 4" }))
    expect(within(form).getAllByLabelText(/^Opzione \d$/)).toHaveLength(3)

    await utente.type(within(form).getByLabelText('Testo della domanda'), 'Quale?')
    await utente.type(within(form).getByLabelText('Spiegazione (mostrata dopo la risposta)'), 'Così')
    for (const [i, testo] of ['A', 'B', 'C'].entries()) {
      await utente.type(within(form).getByLabelText(`Opzione ${i + 1}`), testo)
    }
    await utente.click(within(form).getByRole('button', { name: 'Crea domanda' }))
    expect(within(form).getByText(MESSAGGI.unaCorretta)).toBeInTheDocument()

    // il radio: scelta la prima e poi la seconda, ne resta una sola
    await utente.click(within(form).getByLabelText("L'opzione 1 è la corretta"))
    await utente.click(within(form).getByLabelText("L'opzione 2 è la corretta"))
    expect(within(form).getByLabelText("L'opzione 1 è la corretta")).not.toBeChecked()
    await utente.click(within(form).getByRole('button', { name: 'Crea domanda' }))

    await vi.waitFor(() =>
      expect(chiamate('/api/admin/lessons/l2/questions', 'POST')).toEqual([
        {
          testo: 'Quale?',
          spiegazione: 'Così',
          tipo: 'SCELTA_MULTIPLA',
          opzioni: [
            { testo: 'A', corretta: false },
            { testo: 'B', corretta: true },
            { testo: 'C', corretta: false },
          ],
        },
      ]),
    )
  })

  it('i testi ripetuti segnalati dal backend vanno sotto il campo giusto e il testo resta', async () => {
    const utente = userEvent.setup()
    apri(
      { l2: [json(dettaglio('l2', []))] },
      {
        'POST /api/admin/lessons/l2/questions': errore400({
          'coppie[1].sinistra': 'Questo elemento è uguale a un altro della colonna di sinistra',
          'coppie[1].destra': 'Questo elemento è uguale a un altro della colonna di destra',
        }),
      },
    )
    await scegli(utente, 'Sudore')
    await utente.click(await within(editor()).findByRole('button', { name: '+ Nuova domanda' }))
    const form = within(editor()).getByRole('form', { name: 'Nuova domanda' })
    await utente.click(within(form).getByLabelText('Abbinamento'))
    await utente.type(within(form).getByLabelText('Testo della domanda'), 'Abbina')
    await utente.type(within(form).getByLabelText('Spiegazione (mostrata dopo la risposta)'), 'Così')
    await utente.type(within(form).getByLabelText('Sinistra 1'), 'Bicipite')
    await utente.type(within(form).getByLabelText('Destra 1'), 'Braccio')
    await utente.type(within(form).getByLabelText('Sinistra 2'), 'bicipite')
    await utente.type(within(form).getByLabelText('Destra 2'), 'BRACCIO')
    await utente.click(within(form).getByRole('button', { name: 'Crea domanda' }))

    const sinistra2 = within(form).getByLabelText('Sinistra 2')
    expect(await within(form).findByText('Questo elemento è uguale a un altro della colonna di sinistra')).toBeInTheDocument()
    expect(sinistra2).toHaveAccessibleDescription('Questo elemento è uguale a un altro della colonna di sinistra')
    expect(within(form).getByLabelText('Destra 2')).toHaveAccessibleDescription(
      'Questo elemento è uguale a un altro della colonna di destra',
    )
    expect(sinistra2).toHaveValue('bicipite')
    expect(sinistra2).toHaveFocus()
    expect(chiamate('/api/admin/lessons/l2/questions', 'POST')[0].coppie).toEqual([
      { sinistra: 'Bicipite', destra: 'Braccio' },
      { sinistra: 'bicipite', destra: 'BRACCIO' },
    ])
  })
})

describe('modifica e cancellazione', () => {
  it('modifica dei testi: tutti i testi nel body; un ripetuto torna sotto il campo dell\'opzione', async () => {
    const utente = userEvent.setup()
    apri(
      { l1: [json(dettaglio('l1', [MULTIPLA]))] },
      { 'PATCH /api/admin/questions/d2': errore400({ 'opzioni[1].testo': "Questa opzione è uguale a un'altra" }) },
    )
    await scegli(utente, 'Addominali')
    await utente.click(await within(editor()).findByRole('button', { name: 'Modifica i testi della domanda 2' }))

    const form = within(editor()).getByRole('form', { name: 'Modifica la domanda 2' })
    expect(within(form).getByText(/elimina la domanda e creane una nuova/)).toBeInTheDocument()
    const opzione2 = within(form).getByLabelText('Opzione 2')
    await utente.clear(opzione2)
    await utente.type(opzione2, 'deficit calorico')
    await utente.click(within(form).getByRole('button', { name: 'Salva testi' }))

    expect(await within(form).findByText("Questa opzione è uguale a un'altra")).toBeInTheDocument()
    expect(opzione2).toHaveAccessibleDescription("Questa opzione è uguale a un'altra")
    expect(opzione2).toHaveValue('deficit calorico')
    expect(chiamate('/api/admin/questions/d2', 'PATCH')).toEqual([
      {
        testo: 'Cosa riduce il grasso?',
        spiegazione: 'Il deficit calorico',
        opzioni: [
          { id: 'o1', testo: 'Deficit calorico' },
          { id: 'o2', testo: 'deficit calorico' },
        ],
      },
    ])
  })

  it('abbinamento: gli elementi nel body nell\'ordine sinistro, destro di ogni coppia; dopo il salvataggio la lezione resta scelta', async () => {
    const utente = userEvent.setup()
    apri({ l1: [json(dettaglio('l1', [ABBINAMENTO]))] })
    await scegli(utente, 'Addominali')
    await utente.click(await within(editor()).findByRole('button', { name: 'Modifica i testi della domanda 3' }))
    const form = within(editor()).getByRole('form', { name: 'Modifica la domanda 3' })
    await utente.type(within(form).getByLabelText('Destra 2'), ' e gamba')
    await utente.click(within(form).getByRole('button', { name: 'Salva testi' }))

    await vi.waitFor(() => expect(chiamate('/api/admin/lessons/l1', 'GET')).toHaveLength(2))
    expect(chiamate('/api/admin/questions/d3', 'PATCH')[0].elementi).toEqual([
      { id: 's1', testo: 'Bicipite' },
      { id: 'r1', testo: 'Braccio' },
      { id: 's2', testo: 'Quadricipite' },
      { id: 'r2', testo: 'Coscia e gamba' },
    ])
    expect(within(editor()).queryByRole('form')).not.toBeInTheDocument()
    expect(within(editor()).getByRole('heading', { name: 'Addominali' })).toBeInTheDocument()
  })

  it('cancellazione: Annulla non chiama il backend; Elimina una volta sola e la lezione resta scelta', async () => {
    const utente = userEvent.setup()
    apri({ l1: [json(dettaglio('l1', [VERO_FALSO, MULTIPLA])), json(dettaglio('l1', [MULTIPLA]))] })
    await scegli(utente, 'Addominali')

    await utente.click(await within(editor()).findByRole('button', { name: 'Elimina la domanda 1' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Eliminare la domanda «Gli addominali bruciano la pancia?»?')
    await utente.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Annulla' }))
    expect(chiamate('/api/admin/questions/d1', 'DELETE')).toHaveLength(0)

    await utente.click(within(editor()).getByRole('button', { name: 'Elimina la domanda 1' }))
    await utente.dblClick(within(screen.getByRole('dialog')).getByRole('button', { name: 'Elimina' }))

    await vi.waitFor(() => expect(within(editor()).queryByRole('article', { name: 'Domanda 1' })).not.toBeInTheDocument())
    expect(chiamate('/api/admin/questions/d1', 'DELETE')).toHaveLength(1)
    expect(within(editor()).getByRole('heading', { name: 'Addominali' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Domande della lezione «Addominali»' })).toHaveTextContent('Domande (1)')
  })
})
