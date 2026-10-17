/**
 * Regole di validazione dei form, copia "morbida" di quelle del backend: il backend resta autorevole
 * e i suoi errori `campi` hanno sempre la precedenza. Il frontend non deve MAI essere più severo del backend.
 *
 * Fonti da tenere allineate (vedi frontend/CLAUDE.md, Validazione dei form):
 * - backend/docs/specs/auth.md (Registrazione, Login) e i record RegistrazioneRequest / LoginRequest
 * - @PasswordValida e PasswordValidaValidator (backend/CLAUDE.md, Password)
 * Messaggi identici a quelli del backend.
 */

export const MESSAGGI = {
  nomeObbligatorio: 'Il nome è obbligatorio',
  cognomeObbligatorio: 'Il cognome è obbligatorio',
  emailObbligatoria: "L'email è obbligatoria",
  emailNonValida: 'Formato email non valido',
  passwordObbligatoria: 'La password è obbligatoria',
  passwordNonValida:
    'La password deve avere almeno 8 caratteri, una maiuscola, un numero e un carattere speciale (massimo 72 byte)',
  massimo50: 'Massimo 50 caratteri',
  massimo254: 'Massimo 254 caratteri',
} as const

export const MASSIMO_NOME = 50
export const MASSIMO_EMAIL = 254
const MINIMO_CARATTERI_PASSWORD = 8
/** Limite di BCrypt, in byte UTF-8 (PasswordValidaValidator.MASSIMO_BYTE). */
const MASSIMO_BYTE_PASSWORD = 72

/**
 * Spazi secondo Character.isWhitespace di Java (usato da strip() e isBlank()): a differenza di trim() di JavaScript
 * NON comprende gli spazi non separabili (U+00A0, U+2007, U+202F). Con trim() il frontend sarebbe più severo.
 */
const SPAZIO_JAVA = '[\\t\\n\\u000B\\f\\r\\u001C-\\u001F \\u1680\\u2000-\\u2006\\u2008-\\u200A\\u2028\\u2029\\u205F\\u3000]'
const SPAZI_AI_BORDI = new RegExp(`^${SPAZIO_JAVA}+|${SPAZIO_JAVA}+$`, 'gu')
const SOLO_SPAZI = new RegExp(`^${SPAZIO_JAVA}*$`, 'u')
const UN_SPAZIO = new RegExp(`^${SPAZIO_JAVA}$`, 'u')

/** Come String.strip() di Java. */
export function strip(testo: string): string {
  return testo.replace(SPAZI_AI_BORDI, '')
}

/** Come String.isBlank() di Java (vuota o di soli spazi). */
function vuoto(testo: string): boolean {
  return SOLO_SPAZI.test(testo)
}

/** Email come la pulisce il backend prima di validarla: strip() e minuscolo. */
export function pulisciEmail(email: string): string {
  return strip(email).toLowerCase()
}

/**
 * Stessa regexp del backend, (.+@.+\..+), su tutta l'email: qualcosa@dominio.con.punto.
 * Il backend applica anche @Email di Jakarta, più severo: qui non si replica (il frontend resta più morbido).
 */
const FORMATO_EMAIL = /^.+@.+\..+$/u

// --- password ---

export interface RequisitiPassword {
  caratteri: boolean
  maiuscola: boolean
  numero: boolean
  speciale: boolean
}

/**
 * Requisiti di @PasswordValida, uno per uno (anche per il riquadro "Requisiti di sicurezza"):
 * - caratteri contati come code point (un'emoji è un carattere solo)
 * - maiuscola = Character.isUpperCase di Java, cioè la proprietà Unicode Uppercase (anche À)
 * - numero = solo 0-9
 * - speciale = né lettera, né cifra, né spazio (Character.isLetterOrDigit / isWhitespace)
 */
export function requisitiPassword(password: string): RequisitiPassword {
  const codePoint = [...password]
  return {
    caratteri: codePoint.length >= MINIMO_CARATTERI_PASSWORD,
    maiuscola: /\p{Uppercase}/u.test(password),
    numero: /[0-9]/.test(password),
    speciale: codePoint.some((c) => !/[\p{L}\p{Nd}]/u.test(c) && !UN_SPAZIO.test(c)),
  }
}

function passwordValida(password: string): boolean {
  const requisiti = requisitiPassword(password)
  return (
    requisiti.caratteri &&
    requisiti.maiuscola &&
    requisiti.numero &&
    requisiti.speciale &&
    new TextEncoder().encode(password).length <= MASSIMO_BYTE_PASSWORD
  )
}

// --- form ---

export type ErroriCampi = Record<string, string>

export interface DatiRegistrazione {
  nome: string
  cognome: string
  email: string
  password: string
}

/**
 * Errori della registrazione, un solo messaggio per campo e nello stesso ordine del backend:
 * vuoto → obbligatorio (le altre regole non si controllano), poi formato e lunghezza.
 * Le lunghezze si contano dopo la pulizia, come nel backend (le unità UTF-16 di .length sono quelle di @Size).
 */
export function validaRegistrazione(dati: DatiRegistrazione): ErroriCampi {
  const errori: ErroriCampi = {}
  const nome = strip(dati.nome)
  const cognome = strip(dati.cognome)
  const email = pulisciEmail(dati.email)

  if (vuoto(nome)) {
    errori.nome = MESSAGGI.nomeObbligatorio
  } else if (nome.length > MASSIMO_NOME) {
    errori.nome = MESSAGGI.massimo50
  }

  if (vuoto(cognome)) {
    errori.cognome = MESSAGGI.cognomeObbligatorio
  } else if (cognome.length > MASSIMO_NOME) {
    errori.cognome = MESSAGGI.massimo50
  }

  if (vuoto(email)) {
    errori.email = MESSAGGI.emailObbligatoria
  } else if (!FORMATO_EMAIL.test(email)) {
    errori.email = MESSAGGI.emailNonValida
  } else if (email.length > MASSIMO_EMAIL) {
    errori.email = MESSAGGI.massimo254
  }

  if (vuoto(dati.password)) {
    errori.password = MESSAGGI.passwordObbligatoria
  } else if (!passwordValida(dati.password)) {
    errori.password = MESSAGGI.passwordNonValida
  }

  return errori
}

export interface DatiLogin {
  email: string
  password: string
}

/** Login: solo campi obbligatori. Nessuna regola di formato né della password (un'email strana dà 401). */
export function validaLogin(dati: DatiLogin): ErroriCampi {
  const errori: ErroriCampi = {}
  if (vuoto(pulisciEmail(dati.email))) {
    errori.email = MESSAGGI.emailObbligatoria
  }
  if (vuoto(dati.password)) {
    errori.password = MESSAGGI.passwordObbligatoria
  }
  return errori
}
