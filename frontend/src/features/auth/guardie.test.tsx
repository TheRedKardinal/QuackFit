import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { creaStore } from '../../store/store'
import { fetchFinto, profilo, rispostaErrore, rispostaJson, thunkCheFallisce } from '../../testUtili'
import { leggiToken, salvaToken } from '../../store/sessione/tokenSalvato'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

function apri(percorso: string, token: string | null = null) {
  if (token) {
    salvaToken(token)
  }
  const store = creaStore()
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[percorso]}>
        <App />
      </MemoryRouter>
    </Provider>,
  )
  return store
}

describe('guardie delle rotte', () => {
  it('senza token una pagina protetta porta al login', async () => {
    apri('/profile')

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(fetchFinto).not.toHaveBeenCalled()
  })

  it('con il token e l\'email verificata mostra la pagina', async () => {
    fetchFinto.mockResolvedValue(rispostaJson(200, profilo()))
    apri('/profile', 'token-valido')

    expect(await screen.findByRole('heading', { name: 'Mario Rossi' })).toBeInTheDocument()
  })

  it('con l\'email non verificata porta alla schermata del codice', async () => {
    fetchFinto.mockResolvedValue(rispostaJson(200, profilo({ emailVerificata: false })))
    apri('/', 'token-valido')

    expect(await screen.findByRole('heading', { name: 'Verifica la tua email' })).toBeInTheDocument()
  })

  it('un utente non admin su /admin torna alla home', async () => {
    fetchFinto.mockResolvedValue(rispostaJson(200, profilo({ ruolo: 'UTENTE' })))
    apri('/admin', 'token-valido')

    expect(await screen.findByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
  })

  it('un admin vede /admin (la gestione utenti)', async () => {
    fetchFinto
      .mockResolvedValueOnce(rispostaJson(200, profilo({ ruolo: 'ADMIN' })))
      .mockResolvedValueOnce(
        rispostaJson(200, { contenuto: [], pagina: 0, dimensione: 20, totaleElementi: 0, totalePagine: 0 }),
      )
    apri('/admin', 'token-valido')

    expect(await screen.findByRole('heading', { name: 'Gestione utenti' })).toBeInTheDocument()
  })

  it('chi ha già un token non vede il login', async () => {
    fetchFinto.mockResolvedValue(rispostaJson(200, profilo()))
    apri('/login', 'token-valido')

    expect(await screen.findByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
  })

  it('token scaduto (401 su /api/me): logout e login', async () => {
    fetchFinto.mockResolvedValue(rispostaErrore(401, 'NON_AUTENTICATO'))
    apri('/profile', 'token-scaduto')

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(leggiToken()).toBeNull()
  })

  it('401 da una chiamata successiva riporta al login', async () => {
    fetchFinto.mockResolvedValue(rispostaJson(200, profilo()))
    const store = apri('/profile', 'token-valido')
    await screen.findByRole('heading', { name: 'Mario Rossi' })

    await act(() => store.dispatch(thunkCheFallisce('NON_AUTENTICATO')))

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
  })

  it('403 EMAIL_NON_VERIFICATA da una chiamata successiva porta alla schermata del codice', async () => {
    fetchFinto.mockResolvedValue(rispostaJson(200, profilo()))
    const store = apri('/profile', 'token-valido')
    await screen.findByRole('heading', { name: 'Mario Rossi' })

    await act(() => store.dispatch(thunkCheFallisce('EMAIL_NON_VERIFICATA')))

    expect(await screen.findByRole('heading', { name: 'Verifica la tua email' })).toBeInTheDocument()
  })

  it('il pulsante Esci fa logout e porta al login', async () => {
    fetchFinto.mockResolvedValue(rispostaJson(200, profilo()))
    apri('/profile', 'token-valido')
    await screen.findByRole('heading', { name: 'Mario Rossi' })

    await userEvent.click(screen.getByRole('button', { name: 'Esci' }))

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(leggiToken()).toBeNull()
  })
})

describe('avvio con /api/me fallito', () => {
  it('mostra solo l\'errore, senza contenuti protetti e senza riprovare da solo', async () => {
    fetchFinto.mockRejectedValue(new TypeError('Failed to fetch'))
    apri('/profile', 'token-valido')

    expect(await screen.findByRole('alert')).toHaveTextContent('Impossibile contattare il server')
    expect(screen.queryByRole('heading', { name: 'Mario Rossi' })).not.toBeInTheDocument()
    expect(fetchFinto).toHaveBeenCalledTimes(1)
    expect(leggiToken()).toBe('token-valido')
  })

  it('Riprova ricarica il profilo e mostra la pagina', async () => {
    fetchFinto.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    apri('/profile', 'token-valido')
    await screen.findByRole('alert')

    fetchFinto.mockResolvedValue(rispostaJson(200, profilo()))
    await userEvent.click(screen.getByRole('button', { name: 'Riprova' }))

    expect(await screen.findByRole('heading', { name: 'Mario Rossi' })).toBeInTheDocument()
    expect(fetchFinto).toHaveBeenCalledTimes(2)
  })

  it('Esci fa logout e porta al login', async () => {
    fetchFinto.mockResolvedValue(rispostaErrore(500, 'ERRORE_INTERNO'))
    apri('/profile', 'token-valido')
    await screen.findByRole('alert')

    await userEvent.click(screen.getByRole('button', { name: 'Esci' }))

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(leggiToken()).toBeNull()
  })
})
