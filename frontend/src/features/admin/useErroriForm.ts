import { useRef, useState } from 'react'
import type { ErroriCampi } from '../../common/validazione'

/**
 * Errori dei campi di un form con chiavi come quelle del backend (`testo`, `opzioni[1].testo`, …):
 * l'errore di un campo sparisce appena lo si modifica; con nuovi errori il cursore va sul primo campo sbagliato,
 * nell'ordine dei campi registrati con `registra` (l'ordine in cui compaiono nel form).
 */
export function useErroriForm() {
  const [errori, setErrori] = useState<ErroriCampi>({})
  const campi = useRef(new Map<string, HTMLInputElement>())

  function registra(chiave: string) {
    return (elemento: HTMLInputElement | null) => {
      if (elemento) {
        campi.current.set(chiave, elemento)
      } else {
        campi.current.delete(chiave)
      }
    }
  }

  function togli(...chiavi: string[]) {
    setErrori((precedenti) => {
      const altri = { ...precedenti }
      for (const chiave of chiavi) {
        delete altri[chiave]
      }
      return altri
    })
  }

  function mostra(nuovi: ErroriCampi) {
    setErrori(nuovi)
    // la Map conserva l'ordine di inserimento, che segue l'ordine di render dei campi
    const primo = [...campi.current.entries()].find(([chiave]) => nuovi[chiave])
    primo?.[1].focus()
  }

  return { errori, registra, togli, mostra, azzera: () => setErrori({}) }
}
