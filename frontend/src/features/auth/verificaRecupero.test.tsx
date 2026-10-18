import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter, useLocation, type InitialEntry } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { messaggioErrore } from '../../common/messaggiErrore'
import { MESSAGGI } from '../../common/validazione'
import { creaStore } from '../../store/store'
import { reimpostaPassword } from './authThunk'
import { MESSAGGIO_PASSWORD_DIMENTICATA } from './PaginaPasswordDimenticata'
import { MESSAGGIO_PASSWORD_AGGIORNATA } from '../../common/statoVersoLogin'
import { fetchFinto, profilo, rispostaErrore, rispostaJson } from '../../testUtili'
import { leggiToken, salvaToken } from '../../store/sessione/tokenSalvato'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

/** Mostra lo state della posizione corrente, per controllare che il login lo pulisca. */
function StatoPosizione() {
  const posizione = useLocation()
  return <p data-testid="stato">{JSON.stringify(posizione.state)}</p>
}

function apri(percorso: InitialEntry, token: string | null = null) {
  if (token) {
    salvaToken(token)
  }
  const store = creaStore()
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[percorso]}>
        <App />
        <StatoPosizione />
      </MemoryRouter>
    </Provider>,
  )
  return store
}

function chiamateA(percorso: string) {
  return fetchFinto.mock.calls.filter(([url]) => String(url).endsWith(percorso))
}

function rispostaInSospeso() {
  let risolvi: (risposta: Response) => void = () => {}
  fetchFinto.mockImplementationOnce(() => new Promise<Response>((r) => (risolvi = r)))
  return (risposta: Response) => risolvi(risposta)
}

/** Apre la verifica con un utente non verificato (il primo fetch è /api/me della sessione). */
async function apriVerifica() {
  fetchFinto.mockResolvedValueOnce(rispostaJson(200, profilo({ emailVerificata: false })))
  apri('/verify-email', 'token-valido')
  await screen.findByRole('heading', { name: 'Verifica la tua email' })
}

async function scriviCodice(utente: ReturnType<typeof userEvent.setup>, codice: string) {
  await utente.click(screen.getByLabelText('Cifra 1 di 6'))
  await utente.keyboard(codice)
}

describe('verifica email', () => {
  it('mostra l\'email dell\'utente', async () => {
    await apriVerifica()

    expect(screen.getByText('mario@example.com')).toBeInTheDocument()
  })

  it('codice giusto: l\'email risulta verificata e si va alla mappa', async () => {
    const utente = userEvent.setup()
    await apriVerifica()
    await scriviCodice(utente, '123456')
    fetchFinto.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await utente.click(screen.getByRole('button', { name: 'Conferma codice' }))

    expect(await screen.findByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
    const [, init] = chiamateA('/api/me/verification')[0]
    expect(JSON.parse(String(init?.body))).toEqual({ codice: '123456' })
    expect(chiamateA('/api/me')).toHaveLength(1) // il profilo non si ricarica
  })

  it('senza codice non invia nulla', async () => {
    const utente = userEvent.setup()
    await apriVerifica()

    await utente.click(screen.getByRole('button', { name: 'Conferma codice' }))

    expect(screen.getByText(MESSAGGI.codiceObbligatorio)).toBeInTheDocument()
    expect(chiamateA('/api/me/verification')).toHaveLength(0)
  })

  it.each(['CODICE_NON_VALIDO', 'CODICE_SCADUTO', 'TROPPI_TENTATIVI'])(
    '%s: messaggio sotto il codice e caselle svuotate',
    async (codice) => {
      const utente = userEvent.setup()
      await apriVerifica()
      await scriviCodice(utente, '000000')
      fetchFinto.mockResolvedValueOnce(rispostaErrore(400, codice))

      await utente.click(screen.getByRole('button', { name: 'Conferma codice' }))

      expect(await screen.findByText(messaggioErrore(codice))).toBeInTheDocument()
      expect(screen.getByLabelText('Cifra 1 di 6')).toHaveValue('')
      expect(screen.getByLabelText('Cifra 1 di 6')).toHaveFocus()
    },
  )

  it('doppio click su "Conferma codice": una sola richiesta', async () => {
    const utente = userEvent.setup()
    await apriVerifica()
    await scriviCodice(utente, '123456')
    const rispondi = rispostaInSospeso()

    await utente.dblClick(screen.getByRole('button', { name: 'Conferma codice' }))

    expect(chiamateA('/api/me/verification')).toHaveLength(1)
    await act(async () => rispondi(rispostaErrore(400, 'CODICE_NON_VALIDO')))
  })

  it('nuovo codice inviato: conferma e pulsante disattivato con il conto alla rovescia', async () => {
    const utente = userEvent.setup()
    await apriVerifica()
    fetchFinto.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await utente.click(screen.getByRole('button', { name: 'Invia nuovo codice' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Ti abbiamo inviato un nuovo codice')
    expect(screen.getByRole('button', { name: /Riprova tra \d+ s/ })).toBeDisabled()
  })

  it("un nuovo codice toglie l'errore del codice precedente", async () => {
    const utente = userEvent.setup()
    await apriVerifica()
    await scriviCodice(utente, '000000')
    fetchFinto.mockResolvedValueOnce(rispostaErrore(400, 'CODICE_SCADUTO'))
    await utente.click(screen.getByRole('button', { name: 'Conferma codice' }))
    await screen.findByText(messaggioErrore('CODICE_SCADUTO'))
    fetchFinto.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await utente.click(screen.getByRole('button', { name: 'Invia nuovo codice' }))

    await screen.findByRole('status')
    expect(screen.queryByText(messaggioErrore('CODICE_SCADUTO'))).not.toBeInTheDocument()
  })

  it('ATTENDI_REINVIO: messaggio e pulsante disattivato', async () => {
    const utente = userEvent.setup()
    await apriVerifica()
    fetchFinto.mockResolvedValueOnce(rispostaErrore(429, 'ATTENDI_REINVIO'))

    await utente.click(screen.getByRole('button', { name: 'Invia nuovo codice' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(messaggioErrore('ATTENDI_REINVIO'))
    expect(screen.getByRole('button', { name: /Riprova tra \d+ s/ })).toBeDisabled()
  })

  it('con l\'email già verificata la pagina rimanda alla mappa', async () => {
    fetchFinto.mockResolvedValueOnce(rispostaJson(200, profilo()))
    apri('/verify-email', 'token-valido')

    expect(await screen.findByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
  })
})

describe('password dimenticata', () => {
  it('messaggio sempre generico, poi alla reimpostazione con l\'email già compilata', async () => {
    const utente = userEvent.setup()
    apri('/forgot-password')
    await utente.type(screen.getByLabelText('Email'), '  Mario@Example.com ')
    fetchFinto.mockResolvedValueOnce(rispostaJson(200, { messaggio: 'dal backend' }))

    await utente.click(screen.getByRole('button', { name: 'Invia il codice' }))

    expect(await screen.findByRole('status')).toHaveTextContent(MESSAGGIO_PASSWORD_DIMENTICATA)
    await utente.click(screen.getByRole('button', { name: 'Inserisci il codice' }))
    expect(await screen.findByRole('heading', { name: 'Scegli una nuova password' })).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveValue('mario@example.com')
    expect(screen.getByTestId('stato')).toHaveTextContent('{"email":"mario@example.com"}')
  })

  it('senza email non invia nulla', async () => {
    const utente = userEvent.setup()
    apri('/forgot-password')

    await utente.click(screen.getByRole('button', { name: 'Invia il codice' }))

    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(MESSAGGI.emailObbligatoria)
    expect(fetchFinto).not.toHaveBeenCalled()
  })
})

describe('reimpostazione della password', () => {
  async function compila(utente: ReturnType<typeof userEvent.setup>) {
    await utente.type(screen.getByLabelText('Email'), 'mario@example.com')
    await scriviCodice(utente, '123456')
    await utente.type(screen.getByLabelText('Nuova password'), 'Nuova123!')
  }

  it('riuscita: login con il messaggio e l\'email; lo state poi si pulisce', async () => {
    const utente = userEvent.setup()
    apri('/reset-password')
    await compila(utente)
    fetchFinto.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await utente.click(screen.getByRole('button', { name: 'Salva la nuova password' }))

    expect(await screen.findByRole('status')).toHaveTextContent(MESSAGGIO_PASSWORD_AGGIORNATA)
    expect(screen.getByLabelText('Email')).toHaveValue('mario@example.com')
    // nello state non passano mai codice o password, e il login lo toglie (con F5 il messaggio non riappare)
    expect(await screen.findByTestId('stato')).toHaveTextContent('null')
    const [, init] = chiamateA('/api/auth/password/reset')[0]
    expect(JSON.parse(String(init?.body))).toEqual({
      email: 'mario@example.com',
      codice: '123456',
      nuovaPassword: 'Nuova123!',
    })
  })

  it('CODICE_NON_VALIDO: messaggio sotto il codice', async () => {
    const utente = userEvent.setup()
    apri('/reset-password')
    await compila(utente)
    fetchFinto.mockResolvedValueOnce(rispostaErrore(400, 'CODICE_NON_VALIDO'))

    await utente.click(screen.getByRole('button', { name: 'Salva la nuova password' }))

    expect(await screen.findByText(messaggioErrore('CODICE_NON_VALIDO'))).toBeInTheDocument()
    expect(screen.getByLabelText('Cifra 1 di 6')).toHaveFocus()
  })

  it('nuova password non valida: non invia nulla', async () => {
    const utente = userEvent.setup()
    apri('/reset-password')
    await utente.type(screen.getByLabelText('Email'), 'mario@example.com')
    await scriviCodice(utente, '123456')
    await utente.type(screen.getByLabelText('Nuova password'), 'debole')

    await utente.click(screen.getByRole('button', { name: 'Salva la nuova password' }))

    expect(screen.getByLabelText('Nuova password')).toHaveAccessibleDescription(MESSAGGI.passwordNonValida)
    expect(fetchFinto).not.toHaveBeenCalled()
  })

  it('"Invia di nuovo il codice": messaggio generico e attesa di 60 secondi', async () => {
    const utente = userEvent.setup()
    apri('/reset-password')
    await utente.type(screen.getByLabelText('Email'), 'mario@example.com')
    fetchFinto.mockResolvedValueOnce(rispostaJson(200, { messaggio: 'dal backend' }))

    await utente.click(screen.getByRole('button', { name: 'Invia di nuovo il codice' }))

    expect(await screen.findByRole('status')).toHaveTextContent(MESSAGGIO_PASSWORD_DIMENTICATA)
    expect(screen.getByRole('button', { name: /Riprova tra \d+ s/ })).toBeDisabled()
  })

  it('dopo il reset il token eventualmente salvato si cancella (logout ovunque)', async () => {
    salvaToken('token-vecchio')
    const store = creaStore()
    fetchFinto.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await store.dispatch(reimpostaPassword({ email: 'mario@example.com', codice: '123456', nuovaPassword: 'Nuova123!' }))

    expect(store.getState().sessione.token).toBeNull()
    expect(leggiToken()).toBeNull()
  })
})

describe('login con lo state del reset', () => {
  it('mostra il messaggio e l\'email, poi pulisce lo state', async () => {
    apri({ pathname: '/login', state: { messaggio: MESSAGGIO_PASSWORD_AGGIORNATA, email: 'mario@example.com' } })

    expect(screen.getByRole('status')).toHaveTextContent(MESSAGGIO_PASSWORD_AGGIORNATA)
    expect(screen.getByLabelText('Email')).toHaveValue('mario@example.com')
    expect(await screen.findByTestId('stato')).toHaveTextContent('null')
  })
})
