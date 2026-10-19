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
import type { ContenutoLezione, DomandaLezione, EsitoRisposta, RispostaRequest } from './tipi'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

const VF: DomandaLezione = {
  id: 'vf',
  testo: 'Lo squat allena le gambe.',
  tipo: 'VERO_FALSO',
  opzioni: [
    { id: 'vero', testo: 'Vero' },
    { id: 'falso', testo: 'Falso' },
  ],
  sinistra: [],
  destra: [],
}

const SM: DomandaLezione = {
  id: 'sm',
  testo: 'Quale muscolo piega il gomito?',
  tipo: 'SCELTA_MULTIPLA',
  opzioni: [
    { id: 'deltoide', testo: 'Il deltoide' },
    { id: 'bicipite', testo: 'Il bicipite' },
    { id: 'tricipite', testo: 'Il tricipite' },
  ],
  sinistra: [],
  destra: [],
}

const AB: DomandaLezione = {
  id: 'ab',
  testo: 'Abbina ogni muscolo alla sua zona',
  tipo: 'ABBINAMENTO',
  opzioni: [],
  // mescolate ognuna per conto suo, come fa il backend
  sinistra: [
    { id: 's1', testo: 'Pettorale' },
    { id: 's2', testo: 'Dorsale' },
  ],
  destra: [
    { id: 'd2', testo: 'Schiena' },
    { id: 'd1', testo: 'Petto' },
  ],
}

/** Soluzioni del backend finto: chi risponde con queste ha risposto giusto. */
const GIUSTE: Record<string, string> = { vf: 'vero', sm: 'bicipite' }
const COPPIE_GIUSTE = [
  { sinistroId: 's1', destroId: 'd1' },
  { sinistroId: 's2', destroId: 'd2' },
]

const CHIUSURA = { esito: 'SUPERATO', errori: 1, streak: { valore: 4, record: 7 } }

interface Opzioni {
  domande?: DomandaLezione[]
  /** Risposta di POST /complete (di default la chiusura riuscita). */
  complete?: () => Response
  /** Risposta forzata di POST /answers (al posto della valutazione finta). */
  risposta?: () => Response | Promise<Response>
  /** Risposta di GET del contenuto. */
  contenuto?: () => Response
  /** Senza tentativoId nello state del router (pagina ricaricata). */
  senzaTentativo?: boolean
}

/**
 * Apre /lessons/l1 come se arrivasse dalla mappa (tentativoId nello state). Il backend finto valuta le risposte con
 * GIUSTE e conta gli errori: al terzo il tentativo è FALLITO. POST /api/attempts ("Riprova") crea "t2".
 */
function apri({ domande = [VF, SM], complete, risposta, contenuto, senzaTentativo }: Opzioni = {}) {
  let errori = 0
  fetchFinto.mockImplementation(async (url, init) => {
    const percorso = String(url).replace(/^https?:\/\/[^/]+/, '')
    const metodo = init?.method ?? 'GET'
    if (percorso === '/api/me') {
      return rispostaJson(200, profilo())
    }
    if (percorso === '/api/path/lessons/l1') {
      const corpo: ContenutoLezione = { id: 'l1', titolo: 'I muscoli', descrizione: 'D', domande }
      return contenuto ? contenuto() : rispostaJson(200, corpo)
    }
    if (percorso === '/api/path/stages') {
      return rispostaJson(200, [])
    }
    if (percorso === '/api/attempts' && metodo === 'POST') {
      errori = 0
      return rispostaJson(201, { tentativoId: 't2' })
    }
    if (percorso.endsWith('/answers')) {
      if (risposta) {
        return risposta()
      }
      const corpo = JSON.parse(String(init?.body)) as RispostaRequest
      let corretta: boolean
      if ('coppie' in corpo) {
        // un errore per ogni coppia sbagliata, come il backend
        const sbagliate = corpo.coppie.filter(
          (c) => !COPPIE_GIUSTE.some((g) => g.sinistroId === c.sinistroId && g.destroId === c.destroId),
        ).length
        corretta = sbagliate === 0
        errori = Math.min(3, errori + sbagliate)
      } else {
        corretta = GIUSTE[corpo.domandaId] === corpo.opzioneId
        errori = corretta ? errori : Math.min(3, errori + 1)
      }
      const abbinamento = 'coppie' in corpo
      const esito: EsitoRisposta = {
        corretta,
        soluzione: {
          opzioneId: abbinamento ? null : GIUSTE[corpo.domandaId],
          coppie: abbinamento ? COPPIE_GIUSTE : [],
        },
        spiegazione: `Spiegazione di ${corpo.domandaId}`,
        errori,
        esito: errori === 3 ? 'FALLITO' : 'IN_CORSO',
      }
      return rispostaJson(200, esito)
    }
    if (percorso.endsWith('/complete')) {
      return complete ? complete() : rispostaJson(200, CHIUSURA)
    }
    return rispostaErrore(500, 'ERRORE_INTERNO')
  })
  salvaToken('token-valido')
  render(
    <Provider store={creaStore()}>
      <MemoryRouter initialEntries={[{ pathname: '/lessons/l1', state: senzaTentativo ? null : { tentativoId: 't1' } }]}>
        <App />
      </MemoryRouter>
    </Provider>,
  )
}

function chiamate(fine: string) {
  return fetchFinto.mock.calls.filter(([url, init]) => String(url).endsWith(fine) && init?.method === 'POST')
}

/** Sceglie un'opzione e preme "Verifica". */
async function rispondi(utente: ReturnType<typeof userEvent.setup>, testo: string) {
  await utente.click(await screen.findByRole('button', { name: testo }))
  await utente.click(screen.getByRole('button', { name: 'Verifica' }))
}

describe('lezione: domande e drawer di esito', () => {
  it('risposta giusta: drawer verde con spiegazione, poi la domanda successiva', async () => {
    const utente = userEvent.setup()
    apri()

    expect(await screen.findByRole('heading', { name: 'Lo squat allena le gambe.' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Verifica' })).toBeDisabled()
    await rispondi(utente, 'Vero')

    const drawer = await screen.findByRole('dialog', { name: /Risposta giusta!/ })
    expect(drawer).toHaveTextContent('Spiegazione di vf')
    expect(chiamate('/answers')[0][1]?.body).toBe(JSON.stringify({ domandaId: 'vf', opzioneId: 'vero' }))

    await utente.click(screen.getByRole('button', { name: 'Continua' }))
    expect(await screen.findByRole('heading', { name: 'Quale muscolo piega il gomito?' })).toBeInTheDocument()
  })

  it('risposta sbagliata: drawer rosso con la risposta giusta e "Errori 1/3"', async () => {
    const utente = userEvent.setup()
    apri()

    await rispondi(utente, 'Falso')

    const drawer = await screen.findByRole('dialog', { name: /Risposta sbagliata/ })
    expect(drawer).toHaveTextContent('Risposta giusta: Vero')
    expect(screen.getByText('Errori 1/3')).toBeInTheDocument()
  })

  it('un doppio click su "Verifica" invia una sola risposta', async () => {
    const utente = userEvent.setup()
    let rispondiServer: (r: Response) => void = () => {}
    apri({ risposta: () => new Promise<Response>((r) => (rispondiServer = r)) })

    await utente.click(await screen.findByRole('button', { name: 'Vero' }))
    await utente.dblClick(screen.getByRole('button', { name: 'Verifica' }))

    expect(chiamate('/answers')).toHaveLength(1)
    rispondiServer(
      rispostaJson(200, { corretta: true, soluzione: { opzioneId: 'vero', coppie: [] }, spiegazione: 'S', errori: 0, esito: 'IN_CORSO' }),
    )
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  it('dopo l\'esito un click su un\'opzione non fa nulla', async () => {
    const utente = userEvent.setup()
    apri()

    await rispondi(utente, 'Vero')
    await screen.findByRole('dialog')
    await utente.click(screen.getByRole('button', { name: /Falso/ }))

    expect(screen.getByRole('button', { name: /Falso/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Vero/ })).toHaveAttribute('aria-pressed', 'true')
    expect(chiamate('/answers')).toHaveLength(1)
  })

  it('il 400 "già risolta" di un doppio tap si ignora in silenzio', async () => {
    const utente = userEvent.setup()
    apri({
      risposta: () =>
        rispostaJson(400, {
          status: 400,
          codice: 'VALIDAZIONE',
          messaggio: 'Uno o più campi non sono validi',
          campi: { domandaId: 'Domanda già risolta' },
          timestamp: '',
        }),
    })

    await rispondi(utente, 'Vero')

    await vi.waitFor(() => expect(chiamate('/answers')).toHaveLength(1))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

describe('lezione: coda, riproposta e chiusura', () => {
  it('la sbagliata torna a fine giro; risolte tutte si chiude e si vede la vittoria con la streak', async () => {
    const utente = userEvent.setup()
    apri()

    await rispondi(utente, 'Falso') // vf sbagliata
    await utente.click(await screen.findByRole('button', { name: 'Continua' }))
    await rispondi(utente, 'Il bicipite') // sm giusta
    await utente.click(await screen.findByRole('button', { name: 'Continua' }))

    // riproposta
    expect(await screen.findByRole('heading', { name: 'Lo squat allena le gambe.' })).toBeInTheDocument()
    expect(chiamate('/complete')).toHaveLength(0)
    await rispondi(utente, 'Vero')
    await utente.click(await screen.findByRole('button', { name: 'Continua' }))

    expect(await screen.findByRole('heading', { name: 'Lezione completata!' })).toBeInTheDocument()
    expect(screen.getByText('Streak: 4 giorni')).toBeInTheDocument()
    expect(screen.getByText('Record: 7 giorni')).toBeInTheDocument()
    expect(chiamate('/complete')).toHaveLength(1)

    await utente.click(screen.getByRole('button', { name: 'Continua' }))
    expect(await screen.findByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
  })

  it('terzo errore: dopo il drawer la sconfitta con 3/3; "Riprova" avvia un nuovo tentativo', async () => {
    const utente = userEvent.setup()
    apri()

    await rispondi(utente, 'Falso')
    await utente.click(await screen.findByRole('button', { name: 'Continua' }))
    await rispondi(utente, 'Il deltoide')
    await utente.click(await screen.findByRole('button', { name: 'Continua' }))
    await rispondi(utente, 'Falso')
    expect(await screen.findByText('Errori 3/3')).toBeInTheDocument()
    await utente.click(await screen.findByRole('button', { name: 'Continua' }))

    expect(await screen.findByRole('heading', { name: 'Lezione non superata' })).toBeInTheDocument()
    expect(screen.getByText('Errori 3/3', { selector: 'p' })).toBeInTheDocument()
    expect(chiamate('/complete')).toHaveLength(0)

    await utente.click(screen.getByRole('button', { name: 'Riprova' }))

    expect(await screen.findByRole('heading', { name: 'Lo squat allena le gambe.' })).toBeInTheDocument()
    expect(chiamate('/api/attempts')).toHaveLength(1)
    expect(screen.getByText('Errori 0/3')).toBeInTheDocument()
    // le risposte vanno al nuovo tentativo
    await rispondi(utente, 'Vero')
    await screen.findByRole('dialog')
    expect(String(chiamate('/answers').at(-1)?.[0])).toContain('/api/attempts/t2/answers')
  })

  it('"Torna alla mappa" dalla sconfitta porta alla mappa', async () => {
    const utente = userEvent.setup()
    apri({ domande: [VF] })

    for (let i = 0; i < 3; i++) {
      await rispondi(utente, 'Falso')
      await utente.click(await screen.findByRole('button', { name: 'Continua' }))
    }
    await utente.click(await screen.findByRole('button', { name: 'Torna alla mappa' }))

    expect(await screen.findByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
  })
})

describe('lezione: abbinamento', () => {
  /** La tessera con quel testo, collegata o no ("Petto" non deve trovare anche "Pettorale"). */
  const tessera = (testo: string) => new RegExp(`^${testo}(,|$)`)

  /** Collega un sinistro a un destro con due tocchi. */
  async function collega(utente: ReturnType<typeof userEvent.setup>, sinistro: string, destro: string) {
    await utente.click(await screen.findByRole('button', { name: tessera(sinistro) }))
    await utente.click(screen.getByRole('button', { name: tessera(destro) }))
  }

  it('"Verifica" si attiva solo con tutte le coppie; le tessere collegate hanno lo stesso numero', async () => {
    const utente = userEvent.setup()
    apri({ domande: [AB] })

    expect(await screen.findByText('0/2 coppie')).toBeInTheDocument()
    // senza un sinistro selezionato i destri non si toccano
    expect(screen.getByRole('button', { name: tessera('Petto') })).toBeDisabled()

    await collega(utente, 'Pettorale', 'Petto')
    expect(screen.getByText('1/2 coppie')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pettorale, coppia 1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Petto, coppia 1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Verifica' })).toBeDisabled()

    await collega(utente, 'Dorsale', 'Schiena')
    expect(screen.getByText('2/2 coppie')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Verifica' })).toBeEnabled()
  })

  it('spostare un destro già usato lascia il sinistro precedente senza coppia e disattiva "Verifica"', async () => {
    const utente = userEvent.setup()
    apri({ domande: [AB] })

    await collega(utente, 'Pettorale', 'Petto')
    await collega(utente, 'Dorsale', 'Schiena')
    await collega(utente, 'Dorsale', 'Petto')

    expect(screen.getByRole('button', { name: 'Pettorale' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Dorsale, coppia 1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Petto, coppia 1' })).toBeInTheDocument()
    expect(screen.getByText('1/2 coppie')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Verifica' })).toBeDisabled()
  })

  it('invia tutte le coppie insieme, una volta sola anche con un doppio tocco', async () => {
    const utente = userEvent.setup()
    apri({ domande: [AB] })

    await collega(utente, 'Dorsale', 'Schiena')
    await collega(utente, 'Pettorale', 'Petto')
    await utente.dblClick(screen.getByRole('button', { name: 'Verifica' }))

    await screen.findByRole('dialog')
    expect(chiamate('/answers')).toHaveLength(1)
    expect(JSON.parse(String(chiamate('/answers')[0][1]?.body))).toEqual({
      domandaId: 'ab',
      coppie: [
        { sinistroId: 's1', destroId: 'd1' },
        { sinistroId: 's2', destroId: 'd2' },
      ],
    })
  })

  it('esito per coppia nel drawer e sulle tessere; la domanda sbagliata torna con le tessere vuote', async () => {
    const utente = userEvent.setup()
    apri({ domande: [AB] })

    await collega(utente, 'Pettorale', 'Schiena')
    await collega(utente, 'Dorsale', 'Petto')
    await utente.click(screen.getByRole('button', { name: 'Verifica' }))

    const drawer = await screen.findByRole('dialog', { name: /Risposta sbagliata/ })
    expect(drawer).toHaveTextContent('Pettorale → Schiena, sbagliata (giusto: Petto)')
    expect(drawer).toHaveTextContent('Dorsale → Petto, sbagliata (giusto: Schiena)')
    expect(screen.getByText('Errori 2/3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pettorale, coppia 1, sbagliata' })).toBeDisabled()

    await utente.click(screen.getByRole('button', { name: 'Continua' }))

    // riproposta: stessa domanda, nessuna coppia
    expect(await screen.findByText('0/2 coppie')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pettorale' })).toBeEnabled()
  })

  it('una lezione con abbinamento si chiude e mostra la vittoria', async () => {
    const utente = userEvent.setup()
    apri({ domande: [VF, AB] })

    await rispondi(utente, 'Vero')
    await utente.click(await screen.findByRole('button', { name: 'Continua' }))
    await collega(utente, 'Pettorale', 'Petto')
    await collega(utente, 'Dorsale', 'Schiena')
    await utente.click(screen.getByRole('button', { name: 'Verifica' }))
    expect(await screen.findByRole('dialog', { name: /Risposta giusta!/ })).toHaveTextContent('Pettorale → Petto, giusta')
    await utente.click(screen.getByRole('button', { name: 'Continua' }))

    expect(await screen.findByRole('heading', { name: 'Lezione completata!' })).toBeInTheDocument()
    expect(chiamate('/complete')).toHaveLength(1)
  })
})

describe('lezione: uscite ed errori', () => {
  it('la chiusura in alto esce senza chiamare il backend', async () => {
    const utente = userEvent.setup()
    apri()

    await utente.click(await screen.findByRole('button', { name: 'Esci dalla lezione' }))

    expect(await screen.findByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
    expect(chiamate('/answers')).toHaveLength(0)
  })

  it('409 TENTATIVO_CHIUSO su una risposta: si torna alla mappa con il messaggio', async () => {
    const utente = userEvent.setup()
    apri({ risposta: () => rispostaErrore(409, 'TENTATIVO_CHIUSO') })

    await rispondi(utente, 'Vero')

    expect(await screen.findByText(messaggioErrore('TENTATIVO_CHIUSO'))).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
  })

  it('senza tentativo nello state (pagina ricaricata) si torna alla mappa', async () => {
    apri({ senzaTentativo: true })

    expect(await screen.findByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
  })

  it('contenuto non più disponibile: si torna alla mappa con il messaggio', async () => {
    apri({ contenuto: () => rispostaErrore(403, 'LEZIONE_BLOCCATA') })

    expect(await screen.findByText(messaggioErrore('LEZIONE_BLOCCATA'))).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
  })
})
