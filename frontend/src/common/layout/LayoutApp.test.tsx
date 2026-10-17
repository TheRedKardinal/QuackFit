import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MeResponse } from '../../api/tipi'
import App from '../../App'
import { fetchFinto, profilo, rispostaJson } from '../../features/auth/testUtili'
import { salvaToken } from '../../features/auth/tokenSalvato'
import { creaStore } from '../../store/store'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchFinto.mockReset()
})

/** Apre l'app su un percorso con l'utente già loggato e il profilo dato. */
async function apri(percorso: string, utente: MeResponse = profilo()) {
  salvaToken('token-valido')
  fetchFinto.mockResolvedValueOnce(rispostaJson(200, utente))
  render(
    <Provider store={creaStore()}>
      <MemoryRouter initialEntries={[percorso]}>
        <App />
      </MemoryRouter>
    </Provider>,
  )
  return screen.findByRole('navigation', { name: 'Navigazione principale' })
}

describe('guscio dell\'app', () => {
  it('barra in basso con Percorso e Profilo; la voce attiva ha aria-current', async () => {
    const barra = await apri('/profile')

    const voci = within(barra).getAllByRole('link')
    expect(voci.map((voce) => voce.textContent)).toEqual(['Percorso', 'Profilo'])
    expect(within(barra).getByRole('link', { name: 'Profilo' })).toHaveAttribute('aria-current', 'page')
    expect(within(barra).getByRole('link', { name: 'Percorso' })).not.toHaveAttribute('aria-current')
  })

  it('cliccando una voce si cambia pagina e sezione nella barra in alto', async () => {
    const utente = userEvent.setup()
    const barra = await apri('/profile')

    await utente.click(within(barra).getByRole('link', { name: 'Percorso' }))

    expect(screen.getByRole('heading', { name: 'Percorso' })).toBeInTheDocument()
    expect(within(barra).getByRole('link', { name: 'Percorso' })).toHaveAttribute('aria-current', 'page')
  })

  it('la voce Admin compare solo ad ADMIN e SUPERADMIN', async () => {
    const barra = await apri('/', profilo({ ruolo: 'ADMIN' }))

    expect(within(barra).getByRole('link', { name: 'Admin' })).toHaveAttribute('href', '/admin')
  })

  it('a un utente normale la voce Admin non compare', async () => {
    const barra = await apri('/')

    expect(within(barra).queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument()
  })

  it('la streak è il valore di /api/me, senza calcoli', async () => {
    await apri('/', profilo({ streak: { valore: 7, record: 9 } }))

    expect(screen.getByLabelText('Streak: 7 giorni')).toHaveTextContent('7')
  })

  it('streak di un giorno al singolare', async () => {
    await apri('/', profilo({ streak: { valore: 1, record: 3 } }))

    expect(screen.getByLabelText('Streak: 1 giorno')).toBeInTheDocument()
  })

  it('avatar con le iniziali se non c\'è la foto; porta al profilo', async () => {
    await apri('/')

    const avatar = screen.getByRole('link', { name: 'Il tuo profilo' })
    expect(avatar).toHaveTextContent('MR')
    expect(avatar).toHaveAttribute('href', '/profile')
  })

  it('avatar con la foto se c\'è', async () => {
    await apri('/', profilo({ fotoProfilo: 'https://res.cloudinary.com/foto.jpg' }))

    const avatar = screen.getByRole('link', { name: 'Il tuo profilo' })
    expect(avatar.querySelector('img')).toHaveAttribute('src', 'https://res.cloudinary.com/foto.jpg')
    expect(avatar).not.toHaveTextContent('MR')
  })
})
