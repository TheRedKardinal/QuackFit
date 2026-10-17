import { useRef, useState } from 'react'
import { isDatiErrore, type DatiErrore } from '../../api/ApiError'
import { SECONDI_ATTESA_429, useConteggioAllaRovescia } from '../../common/useConteggioAllaRovescia'

/**
 * Invio di un form di accesso: una sola richiesta alla volta e attesa dopo un 429.
 * - `inviando` disattiva il pulsante; il ref blocca anche un secondo click arrivato prima del nuovo render
 * - con 429 (TROPPE_RICHIESTE) parte il conto alla rovescia e il pulsante resta disattivato
 * Restituisce l'errore del backend (o null se è andata bene) al form, che lo mostra.
 */
export function useInvioForm() {
  const inCorso = useRef(false)
  const [inviando, setInviando] = useState(false)
  const [secondiAttesa, avviaAttesa] = useConteggioAllaRovescia()

  async function invia(operazione: () => Promise<unknown>): Promise<DatiErrore | null> {
    if (inCorso.current || secondiAttesa > 0) {
      return null
    }
    inCorso.current = true
    setInviando(true)
    try {
      await operazione()
      return null
    } catch (errore) {
      const dati: DatiErrore = isDatiErrore(errore)
        ? errore
        : { status: 0, codice: 'ERRORE_INTERNO', messaggio: '', campi: {} }
      if (dati.status === 429) {
        avviaAttesa(SECONDI_ATTESA_429)
      }
      return dati
    } finally {
      inCorso.current = false
      setInviando(false)
    }
  }

  return { invia, inviando, secondiAttesa, bloccato: inviando || secondiAttesa > 0 }
}
