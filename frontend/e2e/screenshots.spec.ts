import { randomBytes } from 'node:crypto'
import { resolve } from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import { avviaLezione1, continua, LEZIONE_1, nuovoUtente, registraEVerifica, rispondi, tessera } from './aiuti'

/**
 * Screenshot del README (docs/screenshots/), lanciato a parte con `npm run screenshots`.
 * Un utente di fantasia completa la Lezione 1 senza errori; lungo la strada si fotografano la domanda di abbinamento
 * a metà, la spiegazione dopo una scelta multipla giusta, la vittoria, la mappa e il profilo.
 */
const CARTELLA_SCREENSHOT = resolve(import.meta.dirname, '../../docs/screenshots')

/** Scatta il solo viewport dopo che font e caricamenti sono finiti; le animazioni finite vanno allo stato finale. */
async function scatta(page: Page, nome: string) {
  await expect(page.getByRole('status')).toHaveCount(0)
  await page.evaluate(() => document.fonts.ready)
  await page.screenshot({ path: resolve(CARTELLA_SCREENSHOT, `${nome}.png`), animations: 'disabled' })
}

/** Testo della domanda mostrata e sue risposte giuste (le domande arrivano mescolate). */
async function domandaMostrata(page: Page) {
  const testo = (await page.locator('#testo-domanda').textContent()) ?? ''
  const domanda = LEZIONE_1.find((d) => testo.includes(d.testo))
  if (!domanda) {
    throw new Error(`Domanda non prevista: ${testo}`)
  }
  return domanda
}

test('screenshot del README', async ({ page }) => {
  // email corta, su una riga sola nel profilo; il suffisso la rende nuova a ogni esecuzione
  await registraEVerifica(page, {
    ...nuovoUtente('screenshot'),
    nome: 'Mario',
    cognome: 'Rossi',
    email: `mario.rossi.${randomBytes(2).toString('hex')}@example.com`,
  })
  await avviaLezione1(page)

  let spiegazioneFatta = false
  for (let i = 0; i < 5; i++) {
    const domanda = await domandaMostrata(page)
    const sceltaMultipla = typeof domanda.giusta === 'string' && !['Vero', 'Falso'].includes(domanda.giusta)

    if (Array.isArray(domanda.giusta)) {
      // abbinamento a metà: due coppie collegate e un terzo elemento selezionato
      const [prima, seconda, terza] = domanda.giusta
      for (const [sinistro, destro] of [prima, seconda]) {
        await tessera(page, sinistro).click()
        await tessera(page, destro).click()
      }
      await tessera(page, terza[0]).click()
      await expect(tessera(page, terza[0])).toHaveAttribute('aria-pressed', 'true')
      await scatta(page, 'domanda')
      // si riparte dalla tessera già selezionata: rispondi() la ripremerebbe e la deselezionerebbe
      await tessera(page, terza[1]).click()
      const [, , , quarta] = domanda.giusta
      await tessera(page, quarta[0]).click()
      await tessera(page, quarta[1]).click()
      await page.getByRole('button', { name: 'Verifica' }).click()
    } else {
      await rispondi(page, 'giusta')
    }

    await expect(page.getByRole('dialog', { name: /Risposta giusta/ })).toBeVisible()
    if (sceltaMultipla && !spiegazioneFatta) {
      await scatta(page, 'spiegazione')
      spiegazioneFatta = true
    }
    await continua(page)
  }

  await expect(page.getByRole('heading', { name: 'Lezione completata!' })).toBeVisible()
  await scatta(page, 'resoconto')

  await page.getByRole('button', { name: 'Rivedi risposte' }).click()
  await page.getByRole('button', { name: 'Torna alla mappa' }).click()
  await expect(page.getByRole('button', { name: /^Lezione 1, I muscoli principali: completata/ })).toBeVisible()
  await page.evaluate(() => window.scrollTo(0, 0))
  await scatta(page, 'percorso')

  await page.goto('/profile')
  await expect(page.getByRole('heading', { name: 'Mario Rossi' })).toBeVisible()
  await scatta(page, 'profilo')
})
