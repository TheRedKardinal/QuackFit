import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { messaggioErrore } from '../../common/messaggiErrore'
import { MESSAGGI } from '../../common/validazione'
import { creaStore } from '../../store/store'
import { fetchFinto, profilo, rispostaErrore, rispostaJson } from '../../testUtili'
import { leggiToken } from '../../store/sessione/tokenSalvato'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

function apri(percorso: string) {
  render(
    <Provider store={creaStore()}>
      <MemoryRouter initialEntries={[percorso]}>
        <App />
      </MemoryRouter>
    </Provider>,
  )
}

/** Richieste fatte a un endpoint (esclude /api/me, chiamato dalla sessione dopo il login). */
function chiamateA(percorso: string) {
  return fetchFinto.mock.calls.filter(([url]) => String(url).endsWith(percorso))
}

/** Risposta che non arriva finché il test non la risolve: per provare il pulsante durante la richiesta. */
function rispostaInSospeso() {
  let risolvi: (risposta: Response) => void = () => {}
  fetchFinto.mockImplementationOnce(() => new Promise<Response>((r) => (risolvi = r)))
  return (risposta: Response) => risolvi(risposta)
}

async function compilaRegistrazione(utente: ReturnType<typeof userEvent.setup>) {
  await utente.type(screen.getByLabelText('Nome'), 'Mario')
  await utente.type(screen.getByLabelText('Cognome'), 'Rossi')
  await utente.type(screen.getByLabelText('Email'), 'mario@example.com')
  await utente.type(screen.getByLabelText('Password'), 'Password1!')
}

async function compilaLogin(utente: ReturnType<typeof userEvent.setup>) {
  await utente.type(screen.getByLabelText('Email'), 'mario@example.com')
  await utente.type(screen.getByLabelText('Password'), 'qualunque')
}

describe('registrazione', () => {
  it('con errori non invia nulla, li mostra e mette il cursore sul primo campo sbagliato', async () => {
    const utente = userEvent.setup()
    apri('/register')

    await utente.type(screen.getByLabelText('Nome'), 'Mario')
    await utente.click(screen.getByRole('button', { name: 'Crea account' }))

    expect(fetchFinto).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Cognome')).toHaveAccessibleDescription(MESSAGGI.cognomeObbligatorio)
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(MESSAGGI.emailObbligatoria)
    expect(screen.getByLabelText('Cognome')).toHaveFocus()
  })

  it('il riquadro dei requisiti si spunta mentre si scrive', async () => {
    const utente = userEvent.setup()
    apri('/register')

    await utente.type(screen.getByLabelText('Password'), 'Abc')

    expect(screen.getByText(/1 maiuscola/)).toHaveTextContent('(rispettato)')
    expect(screen.getByText(/1 numero/)).toHaveTextContent('(non ancora rispettato)')
  })

  it('doppio click su "Crea account": una sola richiesta, pulsante disattivato durante l\'invio', async () => {
    const utente = userEvent.setup()
    apri('/register')
    await compilaRegistrazione(utente)
    const rispondi = rispostaInSospeso()

    await utente.dblClick(screen.getByRole('button', { name: 'Crea account' }))

    expect(chiamateA('/api/auth/register')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Attendi…' })).toBeDisabled()
    await act(async () => rispondi(rispostaErrore(500, 'ERRORE_INTERNO')))
  })

  it('riuscita: salva il token e porta alla schermata del codice', async () => {
    const utente = userEvent.setup()
    apri('/register')
    await compilaRegistrazione(utente)
    fetchFinto
      .mockResolvedValueOnce(rispostaJson(201, { token: 'nuovo-token' }))
      .mockResolvedValueOnce(rispostaJson(200, profilo({ emailVerificata: false })))

    await utente.click(screen.getByRole('button', { name: 'Crea account' }))

    expect(await screen.findByRole('heading', { name: 'Verifica la tua email' })).toBeInTheDocument()
    expect(leggiToken()).toBe('nuovo-token')
    const [, init] = chiamateA('/api/auth/register')[0]
    expect(JSON.parse(String(init?.body))).toEqual({
      nome: 'Mario',
      cognome: 'Rossi',
      email: 'mario@example.com',
      password: 'Password1!',
    })
  })

  it('gli errori `campi` del backend si mostrano sotto i campi', async () => {
    const utente = userEvent.setup()
    apri('/register')
    await compilaRegistrazione(utente)
    fetchFinto.mockResolvedValueOnce(
      rispostaJson(400, {
        status: 400,
        codice: 'VALIDAZIONE',
        messaggio: 'Uno o più campi non sono validi',
        campi: { email: MESSAGGI.emailNonValida },
      }),
    )

    await utente.click(screen.getByRole('button', { name: 'Crea account' }))

    expect(await screen.findByText(MESSAGGI.emailNonValida)).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveFocus()
  })

  it('EMAIL_GIA_USATA: messaggio sotto l\'email con il rimando a "Password dimenticata"', async () => {
    const utente = userEvent.setup()
    apri('/register')
    await compilaRegistrazione(utente)
    fetchFinto.mockResolvedValueOnce(rispostaErrore(409, 'EMAIL_GIA_USATA'))

    await utente.click(screen.getByRole('button', { name: 'Crea account' }))

    expect(await screen.findByText(messaggioErrore('EMAIL_GIA_USATA'))).toBeInTheDocument()
    await utente.click(screen.getByRole('link', { name: 'Password dimenticata?' }))
    expect(await screen.findByRole('heading', { name: 'Password dimenticata' })).toBeInTheDocument()
  })
})

describe('login', () => {
  it('controlla solo i campi obbligatori', async () => {
    const utente = userEvent.setup()
    apri('/login')

    await utente.click(screen.getByRole('button', { name: 'Accedi' }))

    expect(fetchFinto).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(MESSAGGI.emailObbligatoria)
    expect(screen.getByLabelText('Email')).toHaveFocus()
  })

  it('login fallito: sempre il messaggio unico', async () => {
    const utente = userEvent.setup()
    apri('/login')
    await compilaLogin(utente)
    fetchFinto.mockResolvedValueOnce(rispostaErrore(401, 'CREDENZIALI_NON_VALIDE'))

    await utente.click(screen.getByRole('button', { name: 'Accedi' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Email o password non corretti, oppure troppi tentativi: riprova tra qualche minuto.',
    )
  })

  it('doppio click su "Accedi": una sola richiesta', async () => {
    const utente = userEvent.setup()
    apri('/login')
    await compilaLogin(utente)
    const rispondi = rispostaInSospeso()

    await utente.dblClick(screen.getByRole('button', { name: 'Accedi' }))

    expect(chiamateA('/api/auth/login')).toHaveLength(1)
    await act(async () => rispondi(rispostaErrore(401, 'CREDENZIALI_NON_VALIDE')))
  })

  it('email non verificata → schermata del codice', async () => {
    const utente = userEvent.setup()
    apri('/login')
    await compilaLogin(utente)
    fetchFinto
      .mockResolvedValueOnce(rispostaJson(200, { token: 'token-login' }))
      .mockResolvedValueOnce(rispostaJson(200, profilo({ emailVerificata: false })))

    await utente.click(screen.getByRole('button', { name: 'Accedi' }))

    expect(await screen.findByRole('heading', { name: 'Verifica la tua email' })).toBeInTheDocument()
  })

  it('email verificata → mappa', async () => {
    const utente = userEvent.setup()
    apri('/login')
    await compilaLogin(utente)
    fetchFinto
      .mockResolvedValueOnce(rispostaJson(200, { token: 'token-login' }))
      .mockResolvedValueOnce(rispostaJson(200, profilo()))

    await utente.click(screen.getByRole('button', { name: 'Accedi' }))

    expect(await screen.findByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
    expect(leggiToken()).toBe('token-login')
  })

  it('429: messaggio, pulsante disattivato con il conto alla rovescia, riattivato dopo 60 secondi', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const utente = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    apri('/login')
    await compilaLogin(utente)
    fetchFinto.mockResolvedValueOnce(rispostaErrore(429, 'TROPPE_RICHIESTE'))

    await utente.click(screen.getByRole('button', { name: 'Accedi' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(messaggioErrore('TROPPE_RICHIESTE'))
    expect(screen.getByRole('button', { name: /Riprova tra \d+ s/ })).toBeDisabled()

    // un secondo alla volta: ogni scatto del conto alla rovescia programma il successivo dopo il render
    for (let secondo = 0; secondo < 60; secondo++) {
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1000)
      })
    }
    expect(screen.getByRole('button', { name: 'Accedi' })).toBeEnabled()
  })
})
