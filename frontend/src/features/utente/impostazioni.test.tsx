import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MeResponse } from '../../api/tipi'
import App from '../../App'
import { messaggioErrore } from '../../common/messaggiErrore'
import { MESSAGGIO_ACCOUNT_ELIMINATO, MESSAGGIO_EMAIL_AGGIORNATA } from '../../common/statoVersoLogin'
import { MESSAGGI } from '../../common/validazione'
import { leggiToken, salvaToken } from '../../store/sessione/tokenSalvato'
import { creaStore } from '../../store/store'
import { installaCanvasFinto, installaImmagineIlleggibile } from '../../testCanvas'
import { fetchFinto, profilo, rispostaErrore, rispostaJson } from '../../testUtili'

const URL_FOTO = 'https://res.cloudinary.com/q/image/upload/v2/avatar/m.jpg'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', fetchFinto)
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:anteprima'), revokeObjectURL: vi.fn() }))
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  fetchFinto.mockReset()
})

/** Apre la pagina di modifica con l'utente loggato (il primo fetch è /api/me della sessione). */
async function apri(utente: MeResponse = profilo()) {
  salvaToken('token-valido')
  fetchFinto.mockResolvedValueOnce(rispostaJson(200, utente))
  render(
    <Provider store={creaStore()}>
      <MemoryRouter initialEntries={['/profile/edit']}>
        <App />
      </MemoryRouter>
    </Provider>,
  )
  await screen.findByRole('heading', { name: 'Modifica profilo' })
}

function chiamateA(percorso: string, metodo: string) {
  return fetchFinto.mock.calls.filter(([url, init]) => String(url).endsWith(percorso) && init?.method === metodo)
}

const vuota = () => new Response(null, { status: 204 })

describe('foto profilo', () => {
  it('campo per galleria o fotocamera: image/* senza capture', async () => {
    await apri()

    const campo = screen.getByLabelText('Cambia foto')
    expect(campo).toHaveAttribute('accept', 'image/*')
    expect(campo).not.toHaveAttribute('capture')
  })

  it('scelta la foto: anteprima e nessuna chiamata finché non si salva', async () => {
    installaCanvasFinto(4000, 3000)
    const utente = userEvent.setup()
    await apri()

    await utente.upload(screen.getByLabelText('Cambia foto'), new File(['x'], 'grande.jpg', { type: 'image/jpeg' }))

    expect(await screen.findByAltText('Anteprima della nuova foto')).toHaveAttribute('src', 'blob:anteprima')
    expect(chiamateA('/api/me/avatar', 'PATCH')).toHaveLength(0)
  })

  it('"Salva foto": multipart con il campo file e foto aggiornata anche nella barra in alto', async () => {
    installaCanvasFinto(4000, 3000)
    const utente = userEvent.setup()
    await apri()
    await utente.upload(screen.getByLabelText('Cambia foto'), new File(['x'], 'grande.jpg', { type: 'image/jpeg' }))
    fetchFinto.mockResolvedValueOnce(rispostaJson(200, { fotoProfilo: URL_FOTO }))

    await utente.click(await screen.findByRole('button', { name: 'Salva foto' }))

    expect(await screen.findByText('✓ Foto aggiornata.')).toBeInTheDocument()
    const [, init] = chiamateA('/api/me/avatar', 'PATCH')[0]
    const corpo = init?.body as FormData
    expect(corpo.get('file')).toBeInstanceOf(Blob)
    expect((corpo.get('file') as File).name).toBe('avatar.jpg')
    expect(init?.headers).not.toHaveProperty('Content-Type')
    const avatar = screen.getByRole('link', { name: 'Il tuo profilo' })
    expect(avatar.querySelector('img')).toHaveAttribute('src', URL_FOTO)
  })

  it('"Annulla" toglie l\'anteprima', async () => {
    installaCanvasFinto(4000, 3000)
    const utente = userEvent.setup()
    await apri()
    await utente.upload(screen.getByLabelText('Cambia foto'), new File(['x'], 'grande.jpg', { type: 'image/jpeg' }))

    await utente.click(await screen.findByRole('button', { name: 'Annulla' }))

    expect(screen.queryByAltText('Anteprima della nuova foto')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Cambia foto')).toBeInTheDocument()
  })

  it('immagine illeggibile: messaggio, niente anteprima', async () => {
    installaImmagineIlleggibile()
    const utente = userEvent.setup()
    await apri()

    await utente.upload(screen.getByLabelText('Cambia foto'), new File(['x'], 'foto.heic', { type: 'image/heic' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('non si può leggere')
    expect(screen.queryByAltText('Anteprima della nuova foto')).not.toBeInTheDocument()
  })

  it('FILE_NON_VALIDO dal backend: messaggio tradotto', async () => {
    installaCanvasFinto(4000, 3000)
    const utente = userEvent.setup()
    await apri()
    await utente.upload(screen.getByLabelText('Cambia foto'), new File(['x'], 'grande.jpg', { type: 'image/jpeg' }))
    fetchFinto.mockResolvedValueOnce(rispostaErrore(400, 'FILE_NON_VALIDO'))

    await utente.click(await screen.findByRole('button', { name: 'Salva foto' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(messaggioErrore('FILE_NON_VALIDO'))
  })

  it('doppio click su "Salva foto": una sola richiesta', async () => {
    installaCanvasFinto(4000, 3000)
    const utente = userEvent.setup()
    await apri()
    await utente.upload(screen.getByLabelText('Cambia foto'), new File(['x'], 'grande.jpg', { type: 'image/jpeg' }))
    let risolvi: (risposta: Response) => void = () => {}
    fetchFinto.mockImplementationOnce(() => new Promise<Response>((r) => (risolvi = r)))

    await utente.dblClick(await screen.findByRole('button', { name: 'Salva foto' }))

    expect(chiamateA('/api/me/avatar', 'PATCH')).toHaveLength(1)
    await act(async () => risolvi(rispostaJson(200, { fotoProfilo: URL_FOTO })))
  })
})

describe('cambio email', () => {
  async function chiedi(utente: ReturnType<typeof userEvent.setup>, email = 'nuova@example.com') {
    await utente.type(screen.getByLabelText('Nuova email'), email)
    await utente.type(screen.getByLabelText('Password attuale di conferma'), 'Password1!')
    await utente.click(screen.getByRole('button', { name: 'Invia codice di conferma' }))
  }

  it('due passaggi: codice alla nuova email, poi logout e login con la nuova email', async () => {
    const utente = userEvent.setup()
    await apri()
    fetchFinto.mockResolvedValueOnce(vuota())
    await chiedi(utente, '  Nuova@Example.com ')

    expect(await screen.findByText('nuova@example.com')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Riprova tra \d+ s/ })).toBeDisabled()
    await utente.click(screen.getByLabelText('Cifra 1 di 6'))
    await utente.keyboard('123456')
    fetchFinto.mockResolvedValueOnce(vuota())
    await utente.click(screen.getByRole('button', { name: 'Conferma nuova email' }))

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(MESSAGGIO_EMAIL_AGGIORNATA)
    expect(screen.getByLabelText('Email')).toHaveValue('nuova@example.com')
    expect(leggiToken()).toBeNull()
    const [, init] = chiamateA('/api/me/email/confirm', 'POST')[0]
    expect(JSON.parse(String(init?.body))).toEqual({ codice: '123456' })
  })

  it('stessa email (anche con maiuscole): errore sul campo, nessuna chiamata', async () => {
    const utente = userEvent.setup()
    await apri()

    await chiedi(utente, 'MARIO@example.com')

    expect(screen.getByLabelText('Nuova email')).toHaveAccessibleDescription(MESSAGGI.emailUguale)
    expect(chiamateA('/api/me/email', 'POST')).toHaveLength(0)
  })

  it.each([
    ['PASSWORD_ATTUALE_ERRATA', 400, 'Password attuale di conferma'],
    ['EMAIL_GIA_USATA', 409, 'Nuova email'],
  ] as const)('%s alla richiesta: messaggio sotto il campo giusto', async (codice, stato, campo) => {
    const utente = userEvent.setup()
    await apri()
    fetchFinto.mockResolvedValueOnce(rispostaErrore(stato, codice))

    await chiedi(utente)

    expect(await screen.findByText(messaggioErrore(codice))).toBeInTheDocument()
    expect(screen.getByLabelText(campo)).toHaveAccessibleDescription(messaggioErrore(codice))
  })

  it('codice sbagliato: messaggio sotto le caselle, si resta loggati', async () => {
    const utente = userEvent.setup()
    await apri()
    fetchFinto.mockResolvedValueOnce(vuota())
    await chiedi(utente)
    await utente.click(await screen.findByLabelText('Cifra 1 di 6'))
    await utente.keyboard('000000')
    fetchFinto.mockResolvedValueOnce(rispostaErrore(400, 'CODICE_NON_VALIDO'))

    await utente.click(screen.getByRole('button', { name: 'Conferma nuova email' }))

    expect(await screen.findByText(messaggioErrore('CODICE_NON_VALIDO'))).toBeInTheDocument()
    expect(screen.getByLabelText('Cifra 1 di 6')).toHaveValue('')
    expect(leggiToken()).toBe('token-valido')
  })

  it('"Usa un\'altra email" torna alla richiesta', async () => {
    const utente = userEvent.setup()
    await apri()
    fetchFinto.mockResolvedValueOnce(vuota())
    await chiedi(utente)

    await utente.click(await screen.findByRole('button', { name: "Usa un'altra email" }))

    expect(screen.getByLabelText('Nuova email')).toHaveValue('nuova@example.com')
  })
})

describe('cancellazione dell\'account', () => {
  async function apriPannello(utente: ReturnType<typeof userEvent.setup>) {
    await utente.click(screen.getByRole('button', { name: 'Elimina account' }))
  }

  it('secondo passaggio con il testo semplice, poi logout e login con "Account eliminato"', async () => {
    const utente = userEvent.setup()
    await apri()
    await apriPannello(utente)

    expect(screen.getByText(/Questa azione è irreversibile/)).toHaveTextContent(
      'Questa azione è irreversibile: il tuo profilo, la tua email e la tua foto verranno eliminati. ' +
        'I tuoi progressi resteranno solo in forma anonima.',
    )
    await utente.type(screen.getByLabelText('Password'), 'Password1!')
    fetchFinto.mockResolvedValueOnce(vuota())
    await utente.click(screen.getByRole('button', { name: 'Elimina definitivamente' }))

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(MESSAGGIO_ACCOUNT_ELIMINATO)
    expect(screen.getByLabelText('Email')).toHaveValue('')
    expect(leggiToken()).toBeNull()
    const [, init] = chiamateA('/api/me', 'DELETE')[0]
    expect(JSON.parse(String(init?.body))).toEqual({ password: 'Password1!' })
  })

  it('"Annulla" chiude il pannello e svuota la password', async () => {
    const utente = userEvent.setup()
    await apri()
    await apriPannello(utente)
    await utente.type(screen.getByLabelText('Password'), 'Password1!')

    await utente.click(screen.getAllByRole('button', { name: 'Annulla' }).at(-1)!)
    await apriPannello(utente)

    expect(screen.getByLabelText('Password')).toHaveValue('')
  })

  it('password sbagliata: messaggio sotto il campo, si resta loggati', async () => {
    const utente = userEvent.setup()
    await apri()
    await apriPannello(utente)
    await utente.type(screen.getByLabelText('Password'), 'Sbagliata1!')
    fetchFinto.mockResolvedValueOnce(rispostaErrore(400, 'PASSWORD_ATTUALE_ERRATA'))

    await utente.click(screen.getByRole('button', { name: 'Elimina definitivamente' }))

    expect(await screen.findByText(messaggioErrore('PASSWORD_ATTUALE_ERRATA'))).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveFocus()
    expect(leggiToken()).toBe('token-valido')
  })

  it('429: messaggio e pulsante disattivato con il conto alla rovescia', async () => {
    const utente = userEvent.setup()
    await apri()
    await apriPannello(utente)
    await utente.type(screen.getByLabelText('Password'), 'Password1!')
    fetchFinto.mockResolvedValueOnce(rispostaErrore(429, 'TROPPE_RICHIESTE'))

    await utente.click(screen.getByRole('button', { name: 'Elimina definitivamente' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(messaggioErrore('TROPPE_RICHIESTE'))
    expect(screen.getByRole('button', { name: /Riprova tra \d+ s/ })).toBeDisabled()
  })

  it('senza password non invia nulla', async () => {
    const utente = userEvent.setup()
    await apri()
    await apriPannello(utente)

    await utente.click(screen.getByRole('button', { name: 'Elimina definitivamente' }))

    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(MESSAGGI.passwordObbligatoria)
    expect(chiamateA('/api/me', 'DELETE')).toHaveLength(0)
  })

  it('il superadmin non vede il pulsante, solo la spiegazione', async () => {
    await apri(profilo({ ruolo: 'SUPERADMIN' }))

    expect(screen.getByText('Il superadmin non può eliminare il proprio account.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Elimina account' })).not.toBeInTheDocument()
  })
})
