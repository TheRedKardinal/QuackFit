import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MeResponse } from '../../api/tipi'
import App from '../../App'
import { messaggioErrore } from '../../common/messaggiErrore'
import { MESSAGGIO_PASSWORD_AGGIORNATA } from '../../common/statoVersoLogin'
import { MESSAGGI } from '../../common/validazione'
import { leggiToken, salvaToken } from '../../store/sessione/tokenSalvato'
import { creaStore } from '../../store/store'
import { fetchFinto, profilo, rispostaErrore, rispostaJson } from '../../testUtili'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

/** Mostra lo state della posizione corrente: deve contenere solo messaggio ed email. */
function StatoPosizione() {
  const posizione = useLocation()
  return <p data-testid="stato">{JSON.stringify(posizione.state)}</p>
}

/** Apre l'app con l'utente loggato (il primo fetch è /api/me della sessione). */
async function apri(percorso: string, utente: MeResponse = profilo()) {
  salvaToken('token-valido')
  fetchFinto.mockResolvedValueOnce(rispostaJson(200, utente))
  const store = creaStore()
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[percorso]}>
        <App />
        <StatoPosizione />
      </MemoryRouter>
    </Provider>,
  )
  await screen.findByRole('navigation', { name: 'Navigazione principale' })
  return store
}

function chiamateA(percorso: string) {
  return fetchFinto.mock.calls.filter(([url]) => String(url).endsWith(percorso))
}

/** Corpo della prima richiesta con un corpo (la GET di /api/me della sessione non ne ha). */
function corpoDi(percorso: string): unknown {
  const [, init] = chiamateA(percorso).find(([, opzioni]) => opzioni?.body !== undefined) ?? []
  return JSON.parse(String(init?.body))
}

function rispostaInSospeso() {
  let risolvi: (risposta: Response) => void = () => {}
  fetchFinto.mockImplementationOnce(() => new Promise<Response>((r) => (risolvi = r)))
  return (risposta: Response) => risolvi(risposta)
}

describe('profilo', () => {
  it('mostra nome, email, ruolo e solo le tre metriche', async () => {
    await apri('/profile', profilo({ ruolo: 'ADMIN', streak: { valore: 4, record: 9 }, lezioniCompletate: 12 }))

    expect(screen.getByRole('heading', { name: 'Mario Rossi' })).toBeInTheDocument()
    expect(screen.getByText('mario@example.com')).toBeInTheDocument()
    // "Admin" è anche una voce della barra in basso: qui si cerca l'etichetta del ruolo
    expect(screen.getByText('Admin', { selector: 'span' })).toBeInTheDocument()
    const metriche = screen.getAllByRole('term').map((termine) => termine.textContent)
    expect(metriche).toEqual(['Streak attuale', 'Record', 'Lezioni completate'])
    const valori = screen.getAllByRole('definition').map((valore) => valore.textContent?.trim())
    expect(valori).toEqual(['4', '9', '12'])
  })

  it('niente email verificata, XP, livelli o badge', async () => {
    await apri('/profile')

    expect(screen.queryByText(/verificat/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/XP|livello|badge|membro da/i)).not.toBeInTheDocument()
  })

  it('"Modifica profilo" porta alla pagina di modifica', async () => {
    const utente = userEvent.setup()
    await apri('/profile')

    await utente.click(screen.getByRole('link', { name: 'Modifica profilo' }))

    expect(screen.getByRole('heading', { name: 'Modifica profilo' })).toBeInTheDocument()
  })
})

describe('modifica dei dati personali', () => {
  it('salva nome e cognome puliti e aggiorna il profilo senza ricaricare /api/me', async () => {
    const utente = userEvent.setup()
    await apri('/profile/edit')
    await utente.clear(screen.getByLabelText('Nome'))
    await utente.type(screen.getByLabelText('Nome'), '  Luigi ')
    fetchFinto.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await utente.click(screen.getByRole('button', { name: 'Salva dati' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Dati salvati')
    expect(corpoDi('/api/me')).toEqual({ nome: '  Luigi ', cognome: 'Rossi' })
    expect(chiamateA('/api/me').filter(([, init]) => (init?.method ?? 'GET') === 'GET')).toHaveLength(1)
    // la barra in alto usa già il nome nuovo (iniziali "LR")
    expect(screen.getByRole('link', { name: 'Il tuo profilo' })).toHaveTextContent('LR')
  })

  it('con errori non invia nulla e mette il cursore sul primo campo sbagliato', async () => {
    const utente = userEvent.setup()
    await apri('/profile/edit')
    await utente.clear(screen.getByLabelText('Nome'))
    await utente.type(screen.getByLabelText('Cognome'), 'a'.repeat(50))

    await utente.click(screen.getByRole('button', { name: 'Salva dati' }))

    expect(screen.getByLabelText('Nome')).toHaveAccessibleDescription(MESSAGGI.nomeObbligatorio)
    expect(screen.getByLabelText('Cognome')).toHaveAccessibleDescription(MESSAGGI.massimo50)
    expect(screen.getByLabelText('Nome')).toHaveFocus()
    expect(chiamateA('/api/me')).toHaveLength(1)
  })

  it('doppio click su "Salva dati": una sola richiesta', async () => {
    const utente = userEvent.setup()
    await apri('/profile/edit')
    const rispondi = rispostaInSospeso()

    await utente.dblClick(screen.getByRole('button', { name: 'Salva dati' }))

    expect(chiamateA('/api/me')).toHaveLength(2)
    await act(async () => rispondi(new Response(null, { status: 204 })))
  })
})

describe('cambio password', () => {
  async function compila(utente: ReturnType<typeof userEvent.setup>, attuale: string, nuova: string) {
    await utente.type(screen.getByLabelText('Password attuale'), attuale)
    await utente.type(screen.getByLabelText('Nuova password'), nuova)
  }

  it('riuscito: logout e login con il messaggio e l\'email, senza password nello state', async () => {
    const utente = userEvent.setup()
    await apri('/profile/edit')
    await compila(utente, 'Password1!', 'Nuova123!')
    fetchFinto.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await utente.click(screen.getByRole('button', { name: 'Aggiorna password' }))

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(MESSAGGIO_PASSWORD_AGGIORNATA)
    expect(screen.getByLabelText('Email')).toHaveValue('mario@example.com')
    expect(leggiToken()).toBeNull()
    expect(corpoDi('/api/me/password')).toEqual({ passwordAttuale: 'Password1!', nuovaPassword: 'Nuova123!' })
    // lo state del router non contiene mai password (e il login poi lo pulisce)
    expect(screen.getByTestId('stato').textContent).not.toContain('Nuova123!')
    expect(screen.getByTestId('stato').textContent).not.toContain('Password1!')
  })

  it('password attuale sbagliata: messaggio sotto il campo, resta loggato', async () => {
    const utente = userEvent.setup()
    await apri('/profile/edit')
    await compila(utente, 'Sbagliata1!', 'Nuova123!')
    fetchFinto.mockResolvedValueOnce(rispostaErrore(400, 'PASSWORD_ATTUALE_ERRATA'))

    await utente.click(screen.getByRole('button', { name: 'Aggiorna password' }))

    expect(await screen.findByText(messaggioErrore('PASSWORD_ATTUALE_ERRATA'))).toBeInTheDocument()
    expect(screen.getByLabelText('Password attuale')).toHaveFocus()
    expect(leggiToken()).toBe('token-valido')
  })

  it('nuova password non valida o uguale all\'attuale: non invia nulla', async () => {
    const utente = userEvent.setup()
    await apri('/profile/edit')
    await compila(utente, 'Password1!', 'Password1!')

    await utente.click(screen.getByRole('button', { name: 'Aggiorna password' }))

    expect(screen.getByLabelText('Nuova password')).toHaveAccessibleDescription(MESSAGGI.passwordUguale)
    expect(chiamateA('/api/me/password')).toHaveLength(0)
  })

  it('il riquadro dei requisiti si spunta mentre si scrive', async () => {
    const utente = userEvent.setup()
    await apri('/profile/edit')

    await utente.type(screen.getByLabelText('Nuova password'), 'Abc1')

    const riquadro = screen.getByRole('region', { name: 'Requisiti di sicurezza' })
    expect(within(riquadro).getByText(/1 numero/)).toHaveTextContent('(rispettato)')
    expect(within(riquadro).getByText(/8\+ caratteri/)).toHaveTextContent('(non ancora rispettato)')
  })

  it('429 TROPPE_RICHIESTE: messaggio e pulsante disattivato', async () => {
    const utente = userEvent.setup()
    await apri('/profile/edit')
    await compila(utente, 'Password1!', 'Nuova123!')
    fetchFinto.mockResolvedValueOnce(rispostaErrore(429, 'TROPPE_RICHIESTE'))

    await utente.click(screen.getByRole('button', { name: 'Aggiorna password' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(messaggioErrore('TROPPE_RICHIESTE'))
    expect(screen.getByRole('button', { name: /Riprova tra \d+ s/ })).toBeDisabled()
  })
})
