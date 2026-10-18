interface Props {
  testo: string
  inviando: boolean
  secondiAttesa: number
}

/** Testo del pulsante di invio: normale, durante la richiesta, o con il conto alla rovescia dopo un 429. */
function TestoPulsante({ testo, inviando, secondiAttesa }: Props) {
  if (secondiAttesa > 0) {
    return <>Riprova tra {secondiAttesa} s</>
  }
  if (inviando) {
    return <>Attendi…</>
  }
  return <>{testo}</>
}

export default TestoPulsante
