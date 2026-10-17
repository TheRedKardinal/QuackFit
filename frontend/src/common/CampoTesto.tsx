import type { ReactNode, Ref } from 'react'
import stili from './CampoTesto.module.css'

interface Props {
  id: string
  etichetta: string
  valore: string
  onChange: (valore: string) => void
  tipo?: 'text' | 'email' | 'password'
  autoComplete?: string
  placeholder?: string
  /** Messaggio d'errore del campo: lo legge anche lo screen reader (aria-describedby). */
  errore?: string
  /** Contenuto a destra dell'etichetta (contatore, "Mostra", link). */
  accessorio?: ReactNode
  /** Contenuto sotto il campo, dopo l'errore (es. un link d'aiuto). */
  children?: ReactNode
  ref?: Ref<HTMLInputElement>
}

/** Campo di un form con etichetta, bordo "push" ed errore sotto il campo. */
function CampoTesto({
  id,
  etichetta,
  valore,
  onChange,
  tipo = 'text',
  autoComplete,
  placeholder,
  errore,
  accessorio,
  children,
  ref,
}: Props) {
  const idErrore = `${id}-errore`

  return (
    <div className={stili.campo}>
      <div className={stili.intestazione}>
        <label htmlFor={id} className={stili.etichetta}>
          {etichetta}
        </label>
        {accessorio && <span className={stili.accessorio}>{accessorio}</span>}
      </div>
      <input
        ref={ref}
        id={id}
        name={id}
        type={tipo}
        value={valore}
        onChange={(evento) => onChange(evento.target.value)}
        autoComplete={autoComplete}
        placeholder={placeholder}
        aria-invalid={errore ? true : undefined}
        aria-describedby={errore ? idErrore : undefined}
        className={`${stili.input} ${errore ? stili.inputErrato : ''}`}
      />
      {errore && (
        <p id={idErrore} className={stili.errore}>
          {errore}
        </p>
      )}
      {children}
    </div>
  )
}

export default CampoTesto
