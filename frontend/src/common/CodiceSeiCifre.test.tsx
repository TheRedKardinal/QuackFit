import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import CodiceSeiCifre from './CodiceSeiCifre'

/** Il campo con il suo stato, come lo usa un form; il valore si legge dal paragrafo sotto. */
function Prova({ iniziale = '' }: { iniziale?: string }) {
  const [valore, setValore] = useState(iniziale)
  return (
    <>
      <CodiceSeiCifre id="codice" etichetta="Codice" valore={valore} onChange={setValore} />
      <p data-testid="valore">{valore}</p>
    </>
  )
}

function casella(numero: number) {
  return screen.getByLabelText(`Cifra ${numero} di 6`)
}

function valore() {
  return screen.getByTestId('valore').textContent
}

describe('CodiceSeiCifre', () => {
  it('scrivendo si passa da una casella alla successiva', async () => {
    const utente = userEvent.setup()
    render(<Prova />)

    await utente.click(casella(1))
    await utente.keyboard('123456')

    expect(valore()).toBe('123456')
    expect(casella(6)).toHaveValue('6')
    expect(casella(6)).toHaveFocus()
  })

  it('accetta solo cifre', async () => {
    const utente = userEvent.setup()
    render(<Prova />)

    await utente.click(casella(1))
    await utente.keyboard('a1 b2')

    expect(valore()).toBe('12')
  })

  it('incollare il codice riempie tutte le caselle (anche con spazi o trattini)', async () => {
    const utente = userEvent.setup()
    render(<Prova />)

    await utente.click(casella(1))
    await utente.paste('123-456')

    expect(valore()).toBe('123456')
  })

  it('Backspace su una casella vuota cancella la cifra precedente', async () => {
    const utente = userEvent.setup()
    render(<Prova iniziale="123" />)

    await utente.click(casella(4))
    await utente.keyboard('{Backspace}')

    expect(valore()).toBe('12')
    expect(casella(3)).toHaveFocus()
  })

  it('una cifra si può correggere scrivendoci sopra', async () => {
    const utente = userEvent.setup()
    render(<Prova iniziale="123456" />)

    await utente.click(casella(2))
    await utente.keyboard('9')

    expect(valore()).toBe('193456')
  })

  it('niente buchi: cliccando una casella oltre le cifre scritte si va alla prima vuota', async () => {
    const utente = userEvent.setup()
    render(<Prova iniziale="12" />)

    await utente.click(casella(5))

    expect(casella(3)).toHaveFocus()
  })
})
