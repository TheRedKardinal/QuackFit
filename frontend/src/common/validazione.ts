/**
 * Regole di validazione dei form, copia "morbida" di quelle del backend: il backend resta autorevole
 * e i suoi errori `campi` hanno sempre la precedenza. Il frontend non deve MAI essere più severo del backend.
 *
 * Fonti da tenere allineate (vedi frontend/CLAUDE.md, Validazione dei form):
 * - backend/docs/specs/auth.md (Registrazione, Login, Verifica email, Password dimenticata, Reimpostazione) e me.md
 *   (Modifica dati, Cambio password), con i record ModificaDatiRequest, CambioPasswordRequest e
 *   RegistrazioneRequest, LoginRequest, VerificaEmailRequest, PasswordDimenticataRequest, ReimpostaPasswordRequest
 * - @PasswordValida e PasswordValidaValidator (backend/CLAUDE.md, Password)
 * Messaggi identici a quelli del backend.
 */

export const MESSAGGI = {
  nomeObbligatorio: 'Il nome è obbligatorio',
  cognomeObbligatorio: 'Il cognome è obbligatorio',
  emailObbligatoria: "L'email è obbligatoria",
  emailNonValida: 'Formato email non valido',
  passwordObbligatoria: 'La password è obbligatoria',
  nuovaPasswordObbligatoria: 'La nuova password è obbligatoria',
  passwordAttualeObbligatoria: 'La password attuale è obbligatoria',
  passwordUguale: 'La nuova password deve essere diversa da quella attuale',
  codiceObbligatorio: 'Il codice è obbligatorio',
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

/** Nome o cognome già puliti: obbligatorio, poi al massimo 50 caratteri (registrazione e modifica dati). */
function erroreNome(valore: string, messaggioObbligatorio: string): string | undefined {
  if (vuoto(valore)) {
    return messaggioObbligatorio
  }
  return valore.length > MASSIMO_NOME ? MESSAGGI.massimo50 : undefined
}

function aggiungiErrore(errori: ErroriCampi, campo: string, errore: string | undefined): void {
  if (errore) {
    errori[campo] = errore
  }
}

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

  aggiungiErrore(errori, 'nome', erroreNome(nome, MESSAGGI.nomeObbligatorio))
  aggiungiErrore(errori, 'cognome', erroreNome(cognome, MESSAGGI.cognomeObbligatorio))

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

/** Verifica dell'email: solo obbligatorio (un codice sbagliato lo segnala il backend, come tentativo). */
export function validaCodice(codice: string): ErroriCampi {
  return vuoto(codice) ? { codice: MESSAGGI.codiceObbligatorio } : {}
}

/** Password dimenticata: solo email obbligatoria, nessun formato (un'email strana riceve la risposta generica). */
export function validaPasswordDimenticata(email: string): ErroriCampi {
  return vuoto(pulisciEmail(email)) ? { email: MESSAGGI.emailObbligatoria } : {}
}

export interface DatiReimpostazione {
  email: string
  codice: string
  nuovaPassword: string
}

/** Reimpostazione: email e codice solo obbligatori, la nuova password con le regole della registrazione. */
export function validaReimpostazione(dati: DatiReimpostazione): ErroriCampi {
  const errori: ErroriCampi = {}
  if (vuoto(pulisciEmail(dati.email))) {
    errori.email = MESSAGGI.emailObbligatoria
  }
  if (vuoto(dati.codice)) {
    errori.codice = MESSAGGI.codiceObbligatorio
  }
  if (vuoto(dati.nuovaPassword)) {
    errori.nuovaPassword = MESSAGGI.nuovaPasswordObbligatoria
  } else if (!passwordValida(dati.nuovaPassword)) {
    errori.nuovaPassword = MESSAGGI.passwordNonValida
  }
  return errori
}

// --- profilo (me.md) ---

export interface DatiProfilo {
  nome: string
  cognome: string
}

/** Modifica dati: nome e cognome con le stesse regole della registrazione. */
export function validaDatiProfilo(dati: DatiProfilo): ErroriCampi {
  const errori: ErroriCampi = {}
  aggiungiErrore(errori, 'nome', erroreNome(strip(dati.nome), MESSAGGI.nomeObbligatorio))
  aggiungiErrore(errori, 'cognome', erroreNome(strip(dati.cognome), MESSAGGI.cognomeObbligatorio))
  return errori
}

export interface DatiCambioPassword {
  passwordAttuale: string
  nuovaPassword: string
}

/**
 * Cambio password: l'attuale solo obbligatoria (sbagliata la segnala il backend), la nuova con le regole della
 * registrazione e diversa dall'attuale (stesso messaggio del backend; qui si controlla prima di inviare).
 */
export function validaCambioPassword(dati: DatiCambioPassword): ErroriCampi {
  const errori: ErroriCampi = {}
  if (vuoto(dati.passwordAttuale)) {
    errori.passwordAttuale = MESSAGGI.passwordAttualeObbligatoria
  }
  if (vuoto(dati.nuovaPassword)) {
    errori.nuovaPassword = MESSAGGI.nuovaPasswordObbligatoria
  } else if (!passwordValida(dati.nuovaPassword)) {
    errori.nuovaPassword = MESSAGGI.passwordNonValida
  } else if (dati.nuovaPassword === dati.passwordAttuale) {
    errori.nuovaPassword = MESSAGGI.passwordUguale
  }
  return errori
}
