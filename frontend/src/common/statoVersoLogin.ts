/**
 * State del router verso il login dopo un'operazione che fa logout ovunque (reset o cambio della password, conferma
 * del cambio email): solo un messaggio e l'email da precompilare, MAI codici o password.
 * Il login lo legge e poi lo toglie dalla cronologia (vedi frontend/CLAUDE.md, Verifica email e recupero password).
 */
export interface StatoVersoLogin {
  messaggio: string
  email: string
}

export const MESSAGGIO_PASSWORD_AGGIORNATA = 'Password aggiornata: accedi con la nuova password.'
export const MESSAGGIO_EMAIL_AGGIORNATA = 'Email aggiornata: accedi con la nuova email.'
/** Dopo la cancellazione dell'account non si precompila nessuna email. */
export const MESSAGGIO_ACCOUNT_ELIMINATO = 'Account eliminato.'
