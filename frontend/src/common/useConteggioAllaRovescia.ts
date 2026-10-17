import { useCallback, useEffect, useState } from 'react'

/** Attesa dopo un 429 (TROPPE_RICHIESTE, ATTENDI_REINVIO): la finestra del backend è di 60 secondi. */
export const SECONDI_ATTESA_429 = 60

/**
 * Conto alla rovescia in secondi: `avvia(n)` parte da n, `secondi` scende di 1 al secondo fino a 0.
 * Serve a tenere disattivato un pulsante dopo un 429 mostrando quanto manca.
 */
export function useConteggioAllaRovescia(): [number, (secondi: number) => void] {
  const [secondi, setSecondi] = useState(0)

  useEffect(() => {
    if (secondi <= 0) {
      return
    }
    const timer = setTimeout(() => setSecondi((s) => s - 1), 1000)
    return () => clearTimeout(timer)
  }, [secondi])

  const avvia = useCallback((n: number) => setSecondi(n), [])
  return [secondi, avvia]
}
