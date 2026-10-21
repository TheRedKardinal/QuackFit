import { expect, test } from '@playwright/test'
import { avviaLezione1, continua, nuovoUtente, registraEVerifica, rispondi } from './aiuti'

test.describe('lezione', () => {
  test('lezione completa senza errori: vittoria con streak, resoconto e lezione completata sulla mappa', async ({
    page,
  }) => {
    await registraEVerifica(page, nuovoUtente('lezione'))
    await avviaLezione1(page)

    // 5 domande, tutte giuste al primo colpo (abbinamento compreso)
    for (let i = 0; i < 5; i++) {
      await rispondi(page, 'giusta')
      await expect(page.getByRole('dialog', { name: /Risposta giusta/ })).toBeVisible()
      await continua(page)
    }

    await expect(page.getByRole('heading', { name: 'Lezione completata!' })).toBeVisible()
    await expect(page.getByText('giorno di streak')).toBeVisible()
    await expect(page.getByText('0/3')).toBeVisible()

    await page.getByRole('button', { name: 'Rivedi risposte' }).click()
    await expect(page.getByRole('heading', { name: 'Resoconto' })).toBeVisible()
    await expect(page.getByText('✓ Corretta')).toHaveCount(5)

    await page.getByRole('button', { name: 'Torna alla mappa' }).click()
    await expect(page.getByRole('button', { name: /^Lezione 1, I muscoli principali: completata/ })).toBeVisible()
    await expect(page.getByRole('button', { name: /^Lezione 2, .*: da fare/ })).toBeVisible()
  })

  test('tre errori: sconfitta e "Riprova" avvia un nuovo tentativo', async ({ page }) => {
    await registraEVerifica(page, nuovoUtente('sconfitta'))
    await avviaLezione1(page)

    // si sbaglia finché la lezione non è persa (l'abbinamento sbagliato vale due errori)
    while (!(await page.getByRole('heading', { name: 'Lezione non superata' }).isVisible())) {
      await rispondi(page, 'sbagliata')
      await continua(page)
    }

    await page.getByRole('button', { name: 'Riprova' }).click()
    await expect(page.getByText('Errori 0/3')).toBeVisible()
    await expect(page.locator('#testo-domanda')).toBeVisible()
  })

  test('la ✕ dopo una risposta chiede conferma: "Resta" torna all\'esito, "Esci" porta alla mappa', async ({ page }) => {
    await registraEVerifica(page, nuovoUtente('uscita'))
    await avviaLezione1(page)
    await rispondi(page, 'giusta')

    await page.getByRole('button', { name: 'Esci dalla lezione' }).click()
    await expect(page.getByRole('dialog', { name: /Vuoi uscire dalla lezione/ })).toBeVisible()

    await page.getByRole('button', { name: 'Resta nella lezione' }).click()
    await expect(page.getByRole('dialog', { name: /Risposta giusta/ })).toBeVisible()

    await page.getByRole('button', { name: 'Esci dalla lezione' }).click()
    await page.getByRole('button', { name: 'Esci', exact: true }).click()
    await expect(page.getByRole('button', { name: /^Lezione 1, I muscoli principali/ })).toBeVisible()
  })
})
