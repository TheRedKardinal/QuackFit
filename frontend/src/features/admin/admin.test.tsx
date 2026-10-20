import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MeResponse } from '../../api/tipi'
import App from '../../App'
import { messaggioErrore } from '../../common/messaggiErrore'
import { salvaToken } from '../../store/sessione/tokenSalvato'
import { creaStore } from '../../store/store'
import { fetchFinto, profilo, rispostaErrore, rispostaJson } from '../../testUtili'
import {
  MOTIVO_SE_STESSO,
  MOTIVO_SOLO_SUPERADMIN,
  MOTIVO_SUPERADMIN,
  azioneRuolo,
} from './azioneRuolo'
import type { Pagina, UtenteAdmin } from './tipi'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

function utente(modifiche: Partial<UtenteAdmin> = {}): UtenteAdmin {
  return {
    id: 'id-giulia',
    nome: 'Giulia',
    cognome: 'Verdi',
    email: 'giulia@example.com',
    ruolo: 'UTENTE',
    emailVerificata: true,
    creatoAt: '2026-02-01T10:00:00Z',
    ...modifiche,
  }
}

function pagina(contenuto: UtenteAdmin[], modifiche: Partial<Pagina<UtenteAdmin>> = {}): Pagina<UtenteAdmin> {
  return { contenuto, pagina: 0, dimensione: 20, totaleElementi: contenuto.length, totalePagine: 1, ...modifiche }
}

const vuota = () => new Response(null, { status: 204 })

/** Chiamate fatte a un percorso (con la query) e un metodo. */
function chiamate(inizio: string, metodo = 'GET') {
  return fetchFinto.mock.calls
    .map(([url, init]) => ({ url: String(url), metodo: init?.method ?? 'GET', corpo: init?.body }))
    .filter((c) => c.url.includes(inizio) && c.metodo === metodo)
}

/**
 * Apre l'app con un utente loggato. Il fetch finto risponde in base al percorso: /api/me con il profilo,
 * la lista utenti con le pagine date (l'ultima si ripete), il cambio ruolo con `ruolo`.
 */
function apri(percorso: string, io: MeResponse, pagine: Pagina<UtenteAdmin>[] = [pagina([])], ruolo = vuota) {
  let chiamataLista = 0
  fetchFinto.mockImplementation(async (url) => {
    const indirizzo = String(url)
    if (indirizzo.endsWith('/api/me')) {
      return rispostaJson(200, io)
    }
    if (indirizzo.includes('/api/admin/users?')) {
      const risposta = pagine[Math.min(chiamataLista, pagine.length - 1)]
      chiamataLista++
      return rispostaJson(200, risposta)
    }
    if (indirizzo.endsWith('/role')) {
      return ruolo()
    }
    throw new Error(`chiamata inattesa: ${indirizzo}`)
  })
  salvaToken('token-valido')
  render(
    <Provider store={creaStore()}>
      <MemoryRouter initialEntries={[percorso]}>
        <App />
      </MemoryRouter>
    </Provider>,
  )
}

const admin = profilo({ nome: 'Anna', cognome: 'Admin', email: 'anna@example.com', ruolo: 'ADMIN' })
const superadmin = profilo({ nome: 'Super', cognome: 'Admin', email: 'super@example.com', ruolo: 'SUPERADMIN' })

describe('azioneRuolo (le stesse regole del backend)', () => {
  it('su sé stessi nessuna azione, anche se si è SUPERADMIN', () => {
    expect(azioneRuolo(admin, utente({ email: 'anna@example.com', ruolo: 'ADMIN' }))).toEqual({
      tipo: 'nessuna',
      motivo: MOTIVO_SE_STESSO,
    })
    expect(azioneRuolo(superadmin, utente({ email: 'super@example.com', ruolo: 'SUPERADMIN' }))).toEqual({
      tipo: 'nessuna',
      motivo: MOTIVO_SE_STESSO,
    })
  })

  it('un SUPERADMIN non si tocca', () => {
    for (const chi of [admin, superadmin]) {
      expect(azioneRuolo(chi, utente({ ruolo: 'SUPERADMIN' }))).toEqual({ tipo: 'nessuna', motivo: MOTIVO_SUPERADMIN })
    }
  })

  it('un UTENTE lo promuovono ADMIN e SUPERADMIN', () => {
    expect(azioneRuolo(admin, utente())).toEqual({ tipo: 'promuovi' })
    expect(azioneRuolo(superadmin, utente())).toEqual({ tipo: 'promuovi' })
  })

  it('un ADMIN lo retrocede solo il SUPERADMIN', () => {
    expect(azioneRuolo(superadmin, utente({ ruolo: 'ADMIN' }))).toEqual({ tipo: 'retrocedi' })
    expect(azioneRuolo(admin, utente({ ruolo: 'ADMIN' }))).toEqual({ tipo: 'nessuna', motivo: MOTIVO_SOLO_SUPERADMIN })
  })
})

describe('pannello admin: rotte e layout', () => {
  it('un utente non admin su /admin/users torna alla home', async () => {
    apri('/admin/users', profilo({ ruolo: 'UTENTE' }))

    expect(await screen.findByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
    expect(chiamate('/api/admin/users')).toHaveLength(0)
  })

  it('/admin porta a /admin/users nel layout admin', async () => {
    apri('/admin', admin)

    expect(await screen.findByRole('heading', { name: 'Gestione utenti' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: "Torna all'app" })).toHaveAttribute('href', '/')
    // la stessa lista di sezioni come sidebar e come barra in basso (sceglie il CSS); Utenti è attivo in entrambe
    const sezioni = screen.getAllByRole('navigation', { name: 'Sezioni admin' })
    expect(sezioni).toHaveLength(2)
    for (const nav of sezioni) {
      expect(within(nav).getByRole('link', { name: 'Utenti' })).toHaveAttribute('aria-current', 'page')
      expect(within(nav).getByRole('link', { name: 'Contenuti' })).toHaveAttribute('href', '/admin/content')
    }
    // fuori da LayoutApp: niente barra Percorso/Profilo
    expect(screen.queryByRole('navigation', { name: 'Navigazione principale' })).not.toBeInTheDocument()
  })
})

describe('gestione utenti', () => {
  it('mostra gli utenti con "Tu" e solo le azioni consentite', async () => {
    apri('/admin/users', admin, [
      pagina([
        utente({ id: 'id-anna', nome: 'Anna', cognome: 'Admin', email: 'anna@example.com', ruolo: 'ADMIN' }),
        utente({ id: 'id-super', nome: 'Super', cognome: 'Admin', email: 'super@example.com', ruolo: 'SUPERADMIN' }),
        utente({ id: 'id-bea', nome: 'Bea', cognome: 'Bianchi', email: 'bea@example.com', ruolo: 'ADMIN' }),
        utente(),
      ]),
    ])

    const tabella = await screen.findByRole('table')
    expect(screen.getByText('4 utenti · Pagina 1 di 1')).toBeInTheDocument()
    const righe = within(tabella).getAllByRole('row').slice(1)
    expect(within(righe[0]).getByText('Tu')).toBeInTheDocument()
    expect(within(righe[0]).getByText(MOTIVO_SE_STESSO)).toBeInTheDocument()
    expect(within(righe[1]).getByText(MOTIVO_SUPERADMIN)).toBeInTheDocument()
    expect(within(righe[2]).getByText(MOTIVO_SOLO_SUPERADMIN)).toBeInTheDocument()
    expect(within(righe[3]).getByRole('button', { name: 'Promuovi ad ADMIN' })).toBeInTheDocument()
    expect(within(tabella).queryByRole('button', { name: 'Retrocedi a UTENTE' })).not.toBeInTheDocument()
  })

  it('la prima richiesta non invia size né q', async () => {
    apri('/admin/users', admin)

    await screen.findByText('Nessun utente trovato.')
    expect(chiamate('/api/admin/users')[0].url).toMatch(/\/api\/admin\/users\?page=0$/)
  })

  it('la ricerca invia q ripulito e torna alla prima pagina; "Pulisci" azzera e ricarica', async () => {
    const utenteUi = userEvent.setup()
    apri('/admin/users', admin, [
      pagina([utente()], { pagina: 0, totalePagine: 2, totaleElementi: 21 }),
      pagina([utente()], { pagina: 1, totalePagine: 2, totaleElementi: 21 }),
      pagina([utente()]),
    ])

    await utenteUi.click(await screen.findByRole('button', { name: 'Successivo' }))
    expect(await screen.findByText('21 utenti · Pagina 2 di 2')).toBeInTheDocument()
    expect(chiamate('/api/admin/users').at(-1)?.url).toContain('page=1')

    await utenteUi.type(screen.getByLabelText('Cerca per nome, cognome o email'), '  giulia v{Enter}')
    await screen.findByText('1 utente · Pagina 1 di 1')
    const ricerca = new URL(chiamate('/api/admin/users').at(-1)!.url, 'http://x')
    expect(ricerca.searchParams.get('page')).toBe('0')
    expect(ricerca.searchParams.get('q')).toBe('giulia v')

    const prima = chiamate('/api/admin/users').length
    await utenteUi.click(screen.getByRole('button', { name: 'Pulisci' }))
    expect(screen.getByLabelText('Cerca per nome, cognome o email')).toHaveValue('')
    await vi.waitFor(() => expect(chiamate('/api/admin/users').length).toBe(prima + 1))
    expect(chiamate('/api/admin/users').at(-1)?.url).toMatch(/\?page=0$/)
  })

  it('Annulla chiude la conferma senza chiamare il backend', async () => {
    const utenteUi = userEvent.setup()
    apri('/admin/users', admin, [pagina([utente()])])

    const tabella = await screen.findByRole('table')
    await utenteUi.click(within(tabella).getByRole('button', { name: 'Promuovi ad ADMIN' }))
    const conferma = screen.getByRole('dialog', { name: /Cambio ruolo/ })
    expect(within(conferma).getByText('Promuovere Giulia Verdi ad ADMIN? Dovrà rifare il login.')).toBeInTheDocument()

    await utenteUi.click(within(conferma).getByRole('button', { name: 'Annulla' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(chiamate('/role', 'PATCH')).toHaveLength(0)
  })

  it('Conferma chiama il backend una volta sola, anche con il doppio click, e ricarica la lista', async () => {
    const utenteUi = userEvent.setup()
    apri('/admin/users', superadmin, [
      pagina([utente({ ruolo: 'ADMIN' })]),
      pagina([utente({ ruolo: 'UTENTE' })]),
    ])

    const tabella = await screen.findByRole('table')
    await utenteUi.click(within(tabella).getByRole('button', { name: 'Retrocedi a UTENTE' }))
    const conferma = screen.getByRole('dialog', { name: /Cambio ruolo/ })
    expect(within(conferma).getByText('Togliere il ruolo ADMIN a Giulia Verdi? Dovrà rifare il login.')).toBeInTheDocument()
    await utenteUi.dblClick(within(conferma).getByRole('button', { name: 'Conferma' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Ruolo di Giulia Verdi aggiornato.')
    const patch = chiamate('/api/admin/users/id-giulia/role', 'PATCH')
    expect(patch).toHaveLength(1)
    expect(JSON.parse(String(patch[0].corpo))).toEqual({ ruolo: 'UTENTE' })
    expect(await within(await screen.findByRole('table')).findByRole('button', { name: 'Promuovi ad ADMIN' })).toBeInTheDocument()
    expect(chiamate('/api/admin/users?')).toHaveLength(2)
  })

  it('un 403 mostra "non hai i permessi" e ricarica la lista', async () => {
    const utenteUi = userEvent.setup()
    apri('/admin/users', admin, [pagina([utente()])], () => rispostaErrore(403, 'ACCESSO_NEGATO'))

    const tabella = await screen.findByRole('table')
    await utenteUi.click(within(tabella).getByRole('button', { name: 'Promuovi ad ADMIN' }))
    await utenteUi.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Conferma' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(messaggioErrore('ACCESSO_NEGATO'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await vi.waitFor(() => expect(chiamate('/api/admin/users?')).toHaveLength(2))
  })
})

describe('gestione utenti: caricamento fallito', () => {
  it('rete o 500 sulla lista: messaggio e "Riprova", che rilegge e mostra la lista', async () => {
    const utenteUi = userEvent.setup()
    let letture = 0
    fetchFinto.mockImplementation(async (url) => {
      const indirizzo = String(url)
      if (indirizzo.endsWith('/api/me')) {
        return rispostaJson(200, admin)
      }
      if (indirizzo.includes('/api/admin/users?')) {
        letture++
        return letture === 1 ? rispostaErrore(500, 'ERRORE_INTERNO') : rispostaJson(200, pagina([utente()]))
      }
      throw new Error(`chiamata inattesa: ${indirizzo}`)
    })
    salvaToken('token-valido')
    render(
      <Provider store={creaStore()}>
        <MemoryRouter initialEntries={['/admin/users']}>
          <App />
        </MemoryRouter>
      </Provider>,
    )

    expect(await screen.findByText(messaggioErrore('ERRORE_INTERNO'))).toBeInTheDocument()
    await utenteUi.click(screen.getByRole('button', { name: 'Riprova' }))

    expect((await screen.findAllByText(/Giulia/)).length).toBeGreaterThan(0)
    expect(letture).toBe(2)
    expect(screen.queryByRole('button', { name: 'Riprova' })).not.toBeInTheDocument()
  })
})
