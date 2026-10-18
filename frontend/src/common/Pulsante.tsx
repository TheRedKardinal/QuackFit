import type { ComponentProps } from 'react'

type Variante = 'primario' | 'successo' | 'neutro' | 'pericolo'

const CLASSI_VARIANTE: Record<Variante, string> = {
  primario: '',
  successo: 'btn-push-successo',
  neutro: 'btn-push-neutro',
  pericolo: 'btn-push-pericolo',
}

interface Props extends ComponentProps<'button'> {
  variante?: Variante
  /** Occupa tutta la larghezza del contenitore. */
  larghezzaPiena?: boolean
}

/**
 * Pulsante "push" (bordo inferiore in rilievo, vedi styles/globali.css). Di default è type="button":
 * per inviare un form va passato type="submit" in modo esplicito.
 */
function Pulsante({ variante = 'primario', larghezzaPiena = false, type = 'button', className, ...altri }: Props) {
  const classi = ['btn-push', CLASSI_VARIANTE[variante], larghezzaPiena ? 'w-100' : '', className ?? '']
    .filter(Boolean)
    .join(' ')
  return <button type={type} className={classi} {...altri} />
}

export default Pulsante
