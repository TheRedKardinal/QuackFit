import { expect, test, type Browser } from '@playwright/test'
import { accedi, nuovoUtente, registraEVerifica } from './aiuti'
import { SUPERADMIN_PROVA } from './ambiente'

/** Una sessione separata (altro utente) con le stesse impostazioni del progetto: indirizzo, dimensioni e touch. */
async function nuovaSessione(browser: Browser) {
  const { baseURL, viewport, hasTouch, isMobile } = test.info().project.use
  const contesto = await browser.newContext({ baseURL, viewport, hasTouch, isMobile })
  return { contesto, pagina: await contesto.newPage() }
}

test.describe('admin', () => {
  test('un utente normale su /admin torna alla mappa', async ({ page }) => {
    await registraEVerifica(page, nuovoUtente('normale'))

    await page.goto('/admin')

    await expect(page.getByRole('heading', { name: 'Percorso' })).toBeAttached()
    await expect(page.getByRole('heading', { name: 'Gestione utenti' })).toHaveCount(0)
  })

  test('il superadmin promuove un utente, che dopo il nuovo login vede il pannello admin', async ({ browser }) => {
    // utente da promuovere, nella sua sessione
    const utente = nuovoUtente('promosso')
    const { contesto: contestoUtente, pagina: paginaUtente } = await nuovaSessione(browser)
    await registraEVerifica(paginaUtente, utente)

    // superadmin di prova, in un'altra sessione
    const { contesto: contestoAdmin, pagina: paginaAdmin } = await nuovaSessione(browser)
    await accedi(paginaAdmin, SUPERADMIN_PROVA.email, SUPERADMIN_PROVA.password)
    // prima di cambiare pagina il login deve essere finito (token salvato, arrivo sulla mappa)
    await expect(paginaAdmin.getByRole('heading', { name: 'Percorso' })).toBeAttached()
    await paginaAdmin.goto('/admin/users')
    await expect(paginaAdmin.getByRole('heading', { name: 'Gestione utenti' })).toBeVisible()
    await paginaAdmin.getByLabel('Cerca per nome, cognome o email').fill(utente.email)
    await paginaAdmin.getByRole('button', { name: 'Cerca' }).click()
    await expect(paginaAdmin.getByText('1 utente')).toBeVisible()
    await paginaAdmin.getByRole('button', { name: 'Promuovi ad ADMIN' }).filter({ visible: true }).click()
    await paginaAdmin.getByRole('dialog').getByRole('button', { name: 'Conferma' }).click()
    await expect(paginaAdmin.getByRole('status')).toBeVisible()

    // il cambio di ruolo fa rifare il login: con il vecchio token si torna al login
    await paginaUtente.goto('/profile')
    await expect(paginaUtente.getByRole('heading', { name: 'Login' })).toBeVisible()
    await accedi(paginaUtente, utente.email, utente.password)
    await expect(paginaUtente.getByRole('heading', { name: 'Percorso' })).toBeAttached()
    await paginaUtente.goto('/admin/users')
    await expect(paginaUtente.getByRole('heading', { name: 'Gestione utenti' })).toBeVisible()

    await contestoUtente.close()
    await contestoAdmin.close()
  })
})
