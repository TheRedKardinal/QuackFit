import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import CoachQuack from './CoachQuack'
import Drawer from './Drawer'
import { messaggioErrore } from './messaggiErrore'
import MessaggioErrore from './MessaggioErrore'
import Pulsante from './Pulsante'

describe('Pulsante', () => {
  it('di default è type="button" (non invia i form per sbaglio)', () => {
    render(<Pulsante>Continua</Pulsante>)

    expect(screen.getByRole('button', { name: 'Continua' })).toHaveAttribute('type', 'button')
  })

  it('applica la variante e passa gli altri attributi', () => {
    render(
      <Pulsante type="submit" variante="successo" larghezzaPiena disabled>
        Conferma
      </Pulsante>,
    )

    const pulsante = screen.getByRole('button', { name: 'Conferma' })
    expect(pulsante).toHaveAttribute('type', 'submit')
    expect(pulsante).toHaveClass('btn-push', 'btn-push-successo', 'w-100')
    expect(pulsante).toBeDisabled()
  })
})

describe('MessaggioErrore', () => {
  it('traduce il codice', () => {
    render(<MessaggioErrore codice="ERRORE_RETE" />)

    expect(screen.getByRole('alert')).toHaveTextContent(messaggioErrore('ERRORE_RETE'))
  })

  it('senza codice né testo non mostra nulla', () => {
    render(<MessaggioErrore testo={null} />)

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

describe('Drawer', () => {
  it('chiuso non mostra nulla', () => {
    render(
      <Drawer aperto={false} tono="successo" titolo="Ottimo lavoro!">
        <p>Spiegazione</p>
      </Drawer>,
    )

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('aperto: dialog con il titolo come nome e il focus su di sé', () => {
    render(
      <Drawer aperto tono="errore" titolo="Risposta sbagliata">
        <p>Spiegazione</p>
      </Drawer>,
    )

    const drawer = screen.getByRole('dialog', { name: /Risposta sbagliata/ })
    expect(drawer).toHaveTextContent('Spiegazione')
    expect(drawer).toHaveFocus()
  })

  it('Esc chiama onChiudi', async () => {
    const utente = userEvent.setup()
    const onChiudi = vi.fn()
    render(
      <Drawer aperto tono="successo" titolo="Ottimo lavoro!" onChiudi={onChiudi}>
        <p>Spiegazione</p>
      </Drawer>,
    )

    await utente.keyboard('{Escape}')

    expect(onChiudi).toHaveBeenCalledTimes(1)
  })
})

describe('CoachQuack', () => {
  it('è decorativo e di default neutro', () => {
    const { container } = render(<CoachQuack />)

    const svg = container.querySelector('svg')
    expect(svg).toHaveAttribute('aria-hidden', 'true')
    expect(svg).toHaveAttribute('data-stato', 'neutro')
    expect(svg).toHaveAttribute('width', '120')
  })

  it.each(['neutro', 'contento', 'triste'] as const)('disegna lo stato %s', (stato) => {
    const { container } = render(<CoachQuack stato={stato} dimensione={72} />)

    const svg = container.querySelector('svg')
    expect(svg).toHaveAttribute('data-stato', stato)
    expect(svg).toHaveAttribute('width', '72')
    expect(svg?.getAttribute('class')).toContain(stato)
  })
})
