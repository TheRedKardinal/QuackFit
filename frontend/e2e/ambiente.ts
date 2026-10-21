import { existsSync, mkdirSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * Ambiente dei test end to end, comune a playwright.config.ts e alle prove: porte, file di log del backend e valori
 * di prova letti da e2e/.env.e2e (escluso da Git; i nomi sono in e2e/.env.e2e.example).
 * backend/env.properties non si legge mai: lo usa solo il backend per utente e password del database.
 */

const cartellaE2e = dirname(fileURLToPath(import.meta.url))

export const PORTA_BACKEND = 8081
export const PORTA_FRONTEND = 5174

const cartellaTmp = resolve(cartellaE2e, '.tmp')
mkdirSync(cartellaTmp, { recursive: true })

/** Il backend delle prove scrive qui il suo log, comprese le email (MAIL_MODALITA=log) con i codici. */
export const FILE_LOG_BACKEND = resolve(cartellaTmp, 'backend.log')

function leggiValoriProva(): Record<string, string> {
  const file = resolve(cartellaE2e, '.env.e2e')
  if (!existsSync(file)) {
    throw new Error('Manca e2e/.env.e2e: copia e2e/.env.e2e.example e compila i valori di prova.')
  }
  const valori: Record<string, string> = {}
  for (const riga of readFileSync(file, 'utf-8').split(/\r?\n/)) {
    const pulita = riga.trim()
    if (!pulita || pulita.startsWith('#') || !pulita.includes('=')) {
      continue
    }
    const indice = pulita.indexOf('=')
    valori[pulita.slice(0, indice)] = pulita.slice(indice + 1)
  }
  return valori
}

export const VALORI_PROVA = leggiValoriProva()

/** Superadmin creato dal backend delle prove al primo avvio (solo sul database quackfit_e2e). */
export const SUPERADMIN_PROVA = {
  email: VALORI_PROVA.E2E_SUPERADMIN_EMAIL,
  password: VALORI_PROVA.E2E_SUPERADMIN_PASSWORD,
}
