import { expect, test } from '@playwright/test'
import { accedi, nuovoUtente, registraEVerifica } from './aiuti'

test.describe('accesso', () => {
  test('registrazione, codice di verifica dal log e arrivo sulla mappa', async ({ page }) => {
    const utente = nuovoUtente('registrazione')

    await registraEVerifica(page, utente)

    await expect(page.getByRole('button', { name: /^Lezione 1, I muscoli principali: da fare/ })).toBeVisible()
  })

  test('login con password sbagliata: messaggio unico, si resta sul login', async ({ page }) => {
    const utente = nuovoUtente('login')
    await registraEVerifica(page, utente)
    await page.evaluate(() => localStorage.clear())

    await accedi(page, utente.email, 'Sbagliata1!')

    await expect(page.getByRole('alert')).toContainText('Email o password non corretti')
    await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible()
  })
})
