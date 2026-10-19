import type { MeResponse } from '../../api/tipi'
import type { UtenteAdmin } from './tipi'

/** Cosa può fare chi è collegato sul ruolo di un utente della lista. */
export type AzioneRuolo =
  | { tipo: 'promuovi' }
  | { tipo: 'retrocedi' }
  | { tipo: 'nessuna'; motivo: string }

// Stessi testi dei 403 del backend (AdminUtentiService)
export const MOTIVO_SE_STESSO = 'Non puoi cambiare il tuo ruolo'
export const MOTIVO_SUPERADMIN = 'Il ruolo del superadmin non si può cambiare'
export const MOTIVO_SOLO_SUPERADMIN = 'Solo il superadmin può togliere il ruolo di admin'

/** Chi è collegato è lui? /api/me non ha l'id: si confronta l'email, unica tra gli utenti attivi. */
export function eSeStesso(chiamante: Pick<MeResponse, 'email'>, utente: UtenteAdmin): boolean {
  return chiamante.email === utente.email
}

/**
 * Azione di ruolo da mostrare (admin.md, Cambio ruolo). È solo comodità: le stesse regole le controlla il backend,
 * che risponde 403 se la pagina non è aggiornata. Nessuno cambia il proprio ruolo né quello del SUPERADMIN;
 * UTENTE → ADMIN lo fanno ADMIN e SUPERADMIN; ADMIN → UTENTE solo il SUPERADMIN.
 */
export function azioneRuolo(chiamante: Pick<MeResponse, 'email' | 'ruolo'>, utente: UtenteAdmin): AzioneRuolo {
  if (eSeStesso(chiamante, utente)) {
    return { tipo: 'nessuna', motivo: MOTIVO_SE_STESSO }
  }
  if (utente.ruolo === 'SUPERADMIN') {
    return { tipo: 'nessuna', motivo: MOTIVO_SUPERADMIN }
  }
  if (utente.ruolo === 'UTENTE') {
    return { tipo: 'promuovi' }
  }
  return chiamante.ruolo === 'SUPERADMIN'
    ? { tipo: 'retrocedi' }
    : { tipo: 'nessuna', motivo: MOTIVO_SOLO_SUPERADMIN }
}
