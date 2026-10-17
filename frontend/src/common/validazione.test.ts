import { describe, expect, it } from 'vitest'
import {
  MESSAGGI,
  requisitiPassword,
  strip,
  validaCodice,
  validaLogin,
  validaPasswordDimenticata,
  validaRegistrazione,
  validaReimpostazione,
  type DatiRegistrazione,
} from './validazione'

// Stessi casi dei test del backend (PasswordValidaValidatorTest, RegistrazioneControllerTest):
// se cambiano lì, vanno aggiornati anche qui.

const VALIDI: DatiRegistrazione = { nome: 'Mario', cognome: 'Rossi', email: 'mario@example.com', password: 'Password1!' }

function erroreDi(campo: keyof DatiRegistrazione, valore: string): string | undefined {
  return validaRegistrazione({ ...VALIDI, [campo]: valore })[campo]
}

describe('validaRegistrazione', () => {
  it('dati validi: nessun errore', () => {
    expect(validaRegistrazione(VALIDI)).toEqual({})
  })

  it.each([
    ['nome', '', MESSAGGI.nomeObbligatorio],
    ['nome', '   ', MESSAGGI.nomeObbligatorio],
    ['nome', 'a'.repeat(51), MESSAGGI.massimo50],
    ['cognome', '', MESSAGGI.cognomeObbligatorio],
    ['cognome', 'a'.repeat(51), MESSAGGI.massimo50],
    ['email', '', MESSAGGI.emailObbligatoria],
    ['email', '   ', MESSAGGI.emailObbligatoria],
    ['email', 'non-una-email', MESSAGGI.emailNonValida],
    ['email', 'mario@localhost', MESSAGGI.emailNonValida],
    ['email', `${'a'.repeat(64)}@${'b'.repeat(60)}.${'c'.repeat(60)}.${'d'.repeat(60)}.${'e'.repeat(7)}`, MESSAGGI.massimo254],
    ['password', '', MESSAGGI.passwordObbligatoria],
    ['password', '   ', MESSAGGI.passwordObbligatoria],
    ['password', 'Pass1!', MESSAGGI.passwordNonValida],
    ['password', 'password1!', MESSAGGI.passwordNonValida],
    ['password', 'Password!!', MESSAGGI.passwordNonValida],
    ['password', 'Password12', MESSAGGI.passwordNonValida],
    ['password', 'Password 12', MESSAGGI.passwordNonValida],
  ] as const)('%s = "%s" → %s', (campo, valore, messaggio) => {
    expect(erroreDi(campo, valore)).toBe(messaggio)
  })

  it('un solo messaggio per campo, anche con più errori', () => {
    expect(validaRegistrazione({ nome: '', cognome: '', email: '', password: '' })).toEqual({
      nome: MESSAGGI.nomeObbligatorio,
      cognome: MESSAGGI.cognomeObbligatorio,
      email: MESSAGGI.emailObbligatoria,
      password: MESSAGGI.passwordObbligatoria,
    })
  })

  it('le lunghezze si contano dopo la pulizia, come nel backend', () => {
    expect(erroreDi('nome', `  ${'a'.repeat(50)}  `)).toBeUndefined()
    expect(erroreDi('email', '  MARIO@Example.COM  ')).toBeUndefined()
  })

  it('gli spazi non separabili non si tolgono (strip di Java): un nome di soli NBSP non è vuoto', () => {
    expect(strip(' Mario ')).toBe(' Mario ')
    expect(erroreDi('nome', ' ')).toBeUndefined()
  })
})

describe('password (@PasswordValida)', () => {
  it.each([
    'Password1!', // caso tipico
    'Àbcdefg1!', // maiuscola accentata
    'Password 1!', // gli spazi sono ammessi
    'Ⓐbcdefg1!', // maiuscola per Java (Other_Uppercase): il frontend non è più severo
  ])('valida: %s', (password) => {
    expect(erroreDi('password', password)).toBeUndefined()
  })

  it('72 byte ammessi, 73 no', () => {
    expect(erroreDi('password', 'Aa1!' + 'a'.repeat(68))).toBeUndefined()
    expect(erroreDi('password', 'Aa1!' + 'a'.repeat(69))).toBe(MESSAGGI.passwordNonValida)
  })

  it('i byte si contano in UTF-8, non i caratteri', () => {
    // 40 caratteri, ma "è" vale 2 byte: 4 + 36 × 2 = 76 byte
    expect(erroreDi('password', 'Aa1!' + 'è'.repeat(36))).toBe(MESSAGGI.passwordNonValida)
  })

  it('i caratteri si contano come code point (un\'emoji è un carattere solo)', () => {
    // 7 code point ma 8 unità UTF-16: non basta
    expect(requisitiPassword('Aa1!😀bc').caratteri).toBe(false)
  })

  it('requisiti uno per uno, per il riquadro della registrazione', () => {
    expect(requisitiPassword('')).toEqual({ caratteri: false, maiuscola: false, numero: false, speciale: false })
    expect(requisitiPassword('abcdefgh')).toEqual({ caratteri: true, maiuscola: false, numero: false, speciale: false })
    expect(requisitiPassword('À1 ')).toEqual({ caratteri: false, maiuscola: true, numero: true, speciale: false })
    expect(requisitiPassword('_')).toEqual({ caratteri: false, maiuscola: false, numero: false, speciale: true })
  })
})

describe('validaLogin', () => {
  it('solo campi obbligatori', () => {
    expect(validaLogin({ email: '  ', password: '' })).toEqual({
      email: MESSAGGI.emailObbligatoria,
      password: MESSAGGI.passwordObbligatoria,
    })
  })

  it('nessuna regola di formato né della password', () => {
    expect(validaLogin({ email: 'non-una-email', password: 'x' })).toEqual({})
  })
})

describe('verifica email e recupero password', () => {
  it('codice: solo obbligatorio', () => {
    expect(validaCodice('')).toEqual({ codice: MESSAGGI.codiceObbligatorio })
    expect(validaCodice('12')).toEqual({})
  })

  it('password dimenticata: solo email obbligatoria, nessun formato', () => {
    expect(validaPasswordDimenticata('   ')).toEqual({ email: MESSAGGI.emailObbligatoria })
    expect(validaPasswordDimenticata('non-una-email')).toEqual({})
  })

  it('reimpostazione: email e codice obbligatori, nuova password con le regole della registrazione', () => {
    expect(validaReimpostazione({ email: '', codice: '', nuovaPassword: '' })).toEqual({
      email: MESSAGGI.emailObbligatoria,
      codice: MESSAGGI.codiceObbligatorio,
      nuovaPassword: MESSAGGI.nuovaPasswordObbligatoria,
    })
    expect(validaReimpostazione({ email: 'strana', codice: '1', nuovaPassword: 'Password 12' })).toEqual({
      nuovaPassword: MESSAGGI.passwordNonValida,
    })
    expect(validaReimpostazione({ email: 'strana', codice: '1', nuovaPassword: 'Àbcdefg1!' })).toEqual({})
  })
})
