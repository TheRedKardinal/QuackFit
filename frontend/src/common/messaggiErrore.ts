import type { CodiceErrore } from '../api/ApiError'

/**
 * File unico che traduce i codici di errore in messaggi per l'utente (frontend/CLAUDE.md, Errori).
 * Dove le spec del backend hanno già un testo (codici email, login) si usa quello.
 * Per VALIDAZIONE i dettagli stanno nei `campi`, mostrati accanto ai campi del form.
 */
const MESSAGGI: Record<CodiceErrore, string> = {
  VALIDAZIONE: 'Controlla i campi evidenziati.',
  CODICE_NON_VALIDO: 'Codice non valido: controlla di averlo scritto bene o richiedine uno nuovo.',
  CODICE_SCADUTO: 'Codice scaduto: richiedine uno nuovo.',
  TROPPI_TENTATIVI: 'Troppi tentativi: richiedi un nuovo codice.',
  PASSWORD_ATTUALE_ERRATA: 'La password attuale non è corretta.',
  FILE_NON_VALIDO: 'Immagine non valida: usa un file jpg, png o webp fino a 2 MB.',
  NON_AUTENTICATO: 'La sessione è scaduta: accedi di nuovo.',
  CREDENZIALI_NON_VALIDE: 'Email o password non corretti, oppure troppi tentativi: riprova tra qualche minuto.',
  ACCESSO_NEGATO: 'Non hai i permessi per questa operazione.',
  EMAIL_NON_VERIFICATA: 'Prima verifica la tua email con il codice che ti abbiamo inviato.',
  LEZIONE_BLOCCATA: 'Questa lezione è ancora bloccata: completa prima quelle precedenti.',
  NON_TROVATO: 'Elemento non trovato.',
  METODO_NON_CONSENTITO: 'Operazione non consentita.',
  EMAIL_GIA_USATA: 'Questa email è già usata da un altro account.',
  TENTATIVO_CHIUSO: 'Questa lezione è già terminata.',
  ATTENDI_REINVIO: 'Attendi un minuto prima di richiedere un nuovo codice.',
  TROPPE_RICHIESTE: 'Troppe richieste: riprova tra un minuto.',
  ERRORE_INTERNO: 'Si è verificato un errore imprevisto, riprova più tardi.',
  ERRORE_RETE: 'Impossibile contattare il server: controlla la connessione e riprova.',
}

/** Messaggio italiano per un codice; un codice sconosciuto riceve il messaggio generico, mai dettagli tecnici. */
export function messaggioErrore(codice: string): string {
  return codice in MESSAGGI ? MESSAGGI[codice as CodiceErrore] : MESSAGGI.ERRORE_INTERNO
}
