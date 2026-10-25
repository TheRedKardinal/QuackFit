import { randomBytes } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { expect, type Page } from '@playwright/test'
import { FILE_LOG_BACKEND } from './ambiente'

/** Utente di prova creato da una prova: email unica a ogni esecuzione, password generata e mai scritta a mano. */
export interface UtenteProva {
  nome: string
  cognome: string
  email: string
  password: string
}

export function nuovoUtente(prefisso: string): UtenteProva {
  const unico = `${Date.now()}-${randomBytes(3).toString('hex')}`
  return {
    nome: 'Prova',
    cognome: 'E2e',
    email: `e2e-${prefisso}-${unico}@example.com`,
    // rispetta le regole della registrazione: maiuscola, numero e carattere speciale
    password: `Qe2!${randomBytes(8).toString('hex')}`,
  }
}

/** Codice dell'ultima email per quell'indirizzo, letto dal log del backend (MAIL_MODALITA=log, invio asincrono). */
export async function codiceDalLog(email: string): Promise<string> {
  let codice: string | null = null
  await expect
    .poll(
      () => {
        const righe = readFileSync(FILE_LOG_BACKEND, 'utf-8')
          .split(/\r?\n/)
          .filter((riga) => riga.includes('[EMAIL SOLO NEL LOG]') && riga.includes(` a ${email} `))
        codice = righe.at(-1)?.match(/codice: (\d{6})/)?.[1] ?? null
        return codice
      },
      { message: `codice di verifica per ${email} nel log del backend`, timeout: 15_000 },
    )
    .not.toBeNull()
  return codice as unknown as string
}

/** Registrazione dal form, codice dal log e arrivo sulla mappa. */
export async function registraEVerifica(page: Page, utente: UtenteProva) {
  await page.goto('/register')
  await page.getByLabel('Nome', { exact: true }).fill(utente.nome)
  await page.getByLabel('Cognome', { exact: true }).fill(utente.cognome)
  await page.getByLabel('Email', { exact: true }).fill(utente.email)
  await page.getByLabel('Password', { exact: true }).fill(utente.password)
  await page.getByRole('checkbox', { name: 'Ho letto Privacy e Termini' }).check()
  await page.getByRole('button', { name: 'Crea account' }).click()

  await expect(page.getByRole('heading', { name: 'Verifica la tua email' })).toBeVisible()
  const codice = await codiceDalLog(utente.email)
  await page.getByLabel('Cifra 1 di 6').click()
  await page.keyboard.type(codice)
  await page.getByRole('button', { name: 'Conferma codice' }).click()
  await expect(page.getByRole('heading', { name: 'Percorso' })).toBeAttached()
}

export async function accedi(page: Page, email: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Accedi' }).click()
}

/** Dalla mappa avvia la Lezione 1 della Tappa 1. */
export async function avviaLezione1(page: Page) {
  await page.getByRole('button', { name: /^Lezione 1, I muscoli principali/ }).click()
  await expect(page.getByRole('button', { name: 'Esci dalla lezione' })).toBeVisible()
}

/**
 * Risposte della Lezione 1 dei contenuti iniziali (ContenutiIniziali), per testo della domanda: giusta e sbagliata.
 * Le domande arrivano mescolate dal backend, quindi si riconoscono dal testo.
 */
const LEZIONE_1: { testo: string; giusta: string[][] | string; sbagliata: string[][] | string }[] = [
  { testo: 'Il quadricipite si trova', giusta: 'Vero', sbagliata: 'Falso' },
  { testo: 'Gli addominali servono solo', giusta: 'Falso', sbagliata: 'Vero' },
  { testo: 'Quale muscolo piega il gomito?', giusta: 'Il bicipite', sbagliata: 'Il tricipite' },
  { testo: 'Che cosa collega un muscolo a un osso?', giusta: 'Il tendine', sbagliata: 'Il legamento' },
  {
    testo: 'Abbina ogni muscolo alla sua zona del corpo.',
    giusta: [
      ['Dorsale', 'Schiena'],
      ['Gluteo', 'Anca e bacino'],
      ['Polpaccio', 'Parte posteriore della gamba'],
      ['Pettorale', 'Petto'],
    ],
    // due coppie scambiate: due errori
    sbagliata: [
      ['Dorsale', 'Petto'],
      ['Pettorale', 'Schiena'],
      ['Gluteo', 'Anca e bacino'],
      ['Polpaccio', 'Parte posteriore della gamba'],
    ],
  },
]

/** Tessera dell'abbinamento per testo, collegata o no ("Petto" non deve trovare anche "Pettorale"). */
function tessera(page: Page, testo: string) {
  return page.getByRole('button', { name: new RegExp(`^${testo}(,|$)`) })
}

/** Risponde alla domanda mostrata (giusta o sbagliata) e preme "Verifica": si apre il drawer dell'esito. */
export async function rispondi(page: Page, comeRispondere: 'giusta' | 'sbagliata') {
  const testoDomanda = (await page.locator('#testo-domanda').textContent()) ?? ''
  const domanda = LEZIONE_1.find((d) => testoDomanda.includes(d.testo))
  if (!domanda) {
    throw new Error(`Domanda non prevista dalle prove: ${testoDomanda}`)
  }
  const risposta = domanda[comeRispondere]
  if (typeof risposta === 'string') {
    await page.getByRole('button', { name: new RegExp(`${risposta}$`) }).click()
  } else {
    for (const [sinistro, destro] of risposta) {
      await tessera(page, sinistro).click()
      await tessera(page, destro).click()
    }
  }
  await page.getByRole('button', { name: 'Verifica' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
}

/** "Continua" nel drawer dell'esito. */
export async function continua(page: Page) {
  await page.getByRole('dialog').getByRole('button', { name: 'Continua' }).click()
}
