/**
 * Regole di validazione dei form, copia "morbida" di quelle del backend: il backend resta autorevole
 * e i suoi errori `campi` hanno sempre la precedenza. Il frontend non deve MAI essere più severo del backend.
 *
 * Fonti da tenere allineate (vedi frontend/CLAUDE.md, Validazione dei form):
 * - backend/docs/specs/auth.md (Registrazione, Login, Verifica email, Password dimenticata, Reimpostazione) e me.md
 *   (Modifica dati, Cambio password, Cambio email, Cancellazione), con i record ModificaDatiRequest,
 *   CambioPasswordRequest, CambioEmailRequest, ConfermaCambioEmailRequest, CancellazioneAccountRequest e
 *   RegistrazioneRequest, LoginRequest, VerificaEmailRequest, PasswordDimenticataRequest, ReimpostaPasswordRequest
 * - @PasswordValida e PasswordValidaValidator (backend/CLAUDE.md, Password)
 * - backend/docs/specs/admin.md (Tappe, Lezioni, Domande) con i record CreazioneTappaRequest, ModificaTappaRequest,
 *   CreazioneLezioneRequest, ModificaLezioneRequest, CreazioneDomandaRequest e ModificaDomandaRequest
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
  emailUguale: 'La nuova email deve essere diversa da quella attuale',
  codiceObbligatorio: 'Il codice è obbligatorio',
  passwordNonValida:
    'La password deve avere almeno 8 caratteri, una maiuscola, un numero e un carattere speciale (massimo 72 byte)',
  massimo50: 'Massimo 50 caratteri',
  massimo254: 'Massimo 254 caratteri',
  titoloObbligatorio: 'Il titolo è obbligatorio',
  descrizioneObbligatoria: 'La descrizione è obbligatoria',
  massimo100: 'Massimo 100 caratteri',
  massimo300: 'Massimo 300 caratteri',
  testoObbligatorio: 'Il testo è obbligatorio',
  spiegazioneObbligatoria: 'La spiegazione è obbligatoria',
  testoVuoto: 'Il testo non può essere vuoto',
  spiegazioneVuota: 'La spiegazione non può essere vuota',
  massimo200: 'Massimo 200 caratteri',
  massimo500: 'Massimo 500 caratteri',
  massimo1000: 'Massimo 1000 caratteri',
  rispostaVero: 'Indica se la risposta giusta è Vero o Falso',
  unaCorretta: 'Una sola opzione deve essere corretta',
} as const

export const MASSIMO_NOME = 50
export const MASSIMO_EMAIL = 254
/** Titolo e descrizione di tappe e lezioni (Tappa/Lezione.MAX_TITOLO e MAX_DESCRIZIONE). */
export const MASSIMO_TITOLO = 100
export const MASSIMO_DESCRIZIONE = 300
/** Domande (Domanda.MAX_TESTO e MAX_SPIEGAZIONE; Opzione ed ElementoAbbinamento.MAX_TESTO). */
export const MASSIMO_TESTO_DOMANDA = 500
export const MASSIMO_SPIEGAZIONE = 1000
export const MASSIMO_TESTO_OPZIONE = 200
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

/** Email già pulita: obbligatoria, poi formato, poi al massimo 254 caratteri (registrazione e cambio email). */
function erroreEmail(email: string): string | undefined {
  if (vuoto(email)) {
    return MESSAGGI.emailObbligatoria
  }
  if (!FORMATO_EMAIL.test(email)) {
    return MESSAGGI.emailNonValida
  }
  return email.length > MASSIMO_EMAIL ? MESSAGGI.massimo254 : undefined
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

  aggiungiErrore(errori, 'email', erroreEmail(email))

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

export interface DatiCambioEmail {
  nuovaEmail: string
  password: string
}

/**
 * Cambio email: nuova email con le regole della registrazione e diversa dall'attuale (confronto dopo la pulizia,
 * come nel backend); password solo obbligatoria (sbagliata la segnala il backend).
 */
export function validaCambioEmail(dati: DatiCambioEmail, emailAttuale: string): ErroriCampi {
  const errori: ErroriCampi = {}
  const nuovaEmail = pulisciEmail(dati.nuovaEmail)
  aggiungiErrore(errori, 'nuovaEmail', erroreEmail(nuovaEmail))
  if (!errori.nuovaEmail && nuovaEmail === emailAttuale) {
    errori.nuovaEmail = MESSAGGI.emailUguale
  }
  if (vuoto(dati.password)) {
    errori.password = MESSAGGI.passwordObbligatoria
  }
  return errori
}

/** Cancellazione dell'account: password solo obbligatoria. */
export function validaCancellazione(password: string): ErroriCampi {
  return vuoto(password) ? { password: MESSAGGI.passwordObbligatoria } : {}
}

// --- admin: tappe e lezioni ---

export interface DatiContenuto {
  titolo: string
  descrizione: string
}

/**
 * Titolo e descrizione di una tappa o di una lezione, in creazione e in modifica: obbligatori e con un massimo,
 * contati dopo strip() come fa il backend. In modifica il frontend invia sempre entrambi i campi, quindi vale la stessa
 * regola (il backend accetta anche un campo assente, ma un testo presente non può essere vuoto).
 */
export function validaContenuto(dati: DatiContenuto): ErroriCampi {
  const errori: ErroriCampi = {}
  const titolo = strip(dati.titolo)
  const descrizione = strip(dati.descrizione)
  if (titolo === '') {
    errori.titolo = MESSAGGI.titoloObbligatorio
  } else if (titolo.length > MASSIMO_TITOLO) {
    errori.titolo = MESSAGGI.massimo100
  }
  if (descrizione === '') {
    errori.descrizione = MESSAGGI.descrizioneObbligatoria
  } else if (descrizione.length > MASSIMO_DESCRIZIONE) {
    errori.descrizione = MESSAGGI.massimo300
  }
  return errori
}

// --- admin: domande ---

/** Un testo obbligatorio con un massimo, contato dopo strip(); undefined se va bene. */
function erroreTesto(valore: string, massimo: number, obbligatorio: string, troppoLungo: string): string | undefined {
  const pulito = strip(valore)
  if (pulito === '') {
    return obbligatorio
  }
  return pulito.length > massimo ? troppoLungo : undefined
}

function erroreOpzione(valore: string): string | undefined {
  return erroreTesto(valore, MASSIMO_TESTO_OPZIONE, MESSAGGI.testoObbligatorio, MESSAGGI.massimo200)
}

function aggiungi(errori: ErroriCampi, chiave: string, errore: string | undefined) {
  if (errore) {
    errori[chiave] = errore
  }
}

export interface DatiNuovaDomanda {
  tipo: 'VERO_FALSO' | 'SCELTA_MULTIPLA' | 'ABBINAMENTO'
  testo: string
  spiegazione: string
  /** VERO_FALSO: null finché non si sceglie. */
  rispostaVero: boolean | null
  /** SCELTA_MULTIPLA: testi delle opzioni e indice di quella corretta (null finché non si sceglie). */
  opzioni: string[]
  corretta: number | null
  /** ABBINAMENTO. */
  coppie: { sinistra: string; destra: string }[]
}

/**
 * Nuova domanda (CreazioneDomandaRequest): solo obbligatori e lunghezze, più la scelta della risposta giusta.
 * Il numero di opzioni o coppie (2–6) lo garantisce il form e il radio permette una sola corretta; i testi ripetuti
 * li segnala il backend (RegoleDomanda). Chiavi uguali a quelle del backend (es. `opzioni[1].testo`).
 */
export function validaNuovaDomanda(dati: DatiNuovaDomanda): ErroriCampi {
  const errori: ErroriCampi = {}
  aggiungi(errori, 'testo', erroreTesto(dati.testo, MASSIMO_TESTO_DOMANDA, MESSAGGI.testoObbligatorio, MESSAGGI.massimo500))
  aggiungi(
    errori,
    'spiegazione',
    erroreTesto(dati.spiegazione, MASSIMO_SPIEGAZIONE, MESSAGGI.spiegazioneObbligatoria, MESSAGGI.massimo1000),
  )
  if (dati.tipo === 'VERO_FALSO' && dati.rispostaVero === null) {
    errori.rispostaVero = MESSAGGI.rispostaVero
  }
  if (dati.tipo === 'SCELTA_MULTIPLA') {
    dati.opzioni.forEach((testo, i) => aggiungi(errori, `opzioni[${i}].testo`, erroreOpzione(testo)))
    if (dati.corretta === null) {
      errori.opzioni = MESSAGGI.unaCorretta
    }
  }
  if (dati.tipo === 'ABBINAMENTO') {
    dati.coppie.forEach((coppia, i) => {
      aggiungi(errori, `coppie[${i}].sinistra`, erroreOpzione(coppia.sinistra))
      aggiungi(errori, `coppie[${i}].destra`, erroreOpzione(coppia.destra))
    })
  }
  return errori
}

export interface DatiModificaDomanda {
  testo: string
  spiegazione: string
  /** Testi di opzioni (SCELTA_MULTIPLA) o elementi (ABBINAMENTO), con la chiave del backend (es. `elementi[3].testo`). */
  testi: { chiave: string; testo: string }[]
}

/**
 * Modifica dei soli testi (ModificaDomandaRequest): si inviano sempre tutti, quindi nessuno può essere vuoto.
 * Testo e spiegazione hanno i messaggi della PATCH ("non può essere vuoto"), opzioni ed elementi quelli di TestoRequest.
 */
export function validaModificaDomanda(dati: DatiModificaDomanda): ErroriCampi {
  const errori: ErroriCampi = {}
  aggiungi(errori, 'testo', erroreTesto(dati.testo, MASSIMO_TESTO_DOMANDA, MESSAGGI.testoVuoto, MESSAGGI.massimo500))
  aggiungi(
    errori,
    'spiegazione',
    erroreTesto(dati.spiegazione, MASSIMO_SPIEGAZIONE, MESSAGGI.spiegazioneVuota, MESSAGGI.massimo1000),
  )
  for (const { chiave, testo } of dati.testi) {
    aggiungi(errori, chiave, erroreOpzione(testo))
  }
  return errori
}
