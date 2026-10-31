import { defineConfig, devices } from '@playwright/test'
import base from './playwright.config'

/**
 * Screenshot del README: stesso stack locale delle prove end to end (backend 8081 su quackfit_e2e, Vite 5174, email
 * solo nel log), ma solo lo script e2e/screenshots.spec.ts e solo il telefono, con densità 2 per immagini nitide.
 * Si lancia a parte con `npm run screenshots`: `npm run e2e` non lo include (testMatch diverso).
 */
export default defineConfig({
  ...base,
  testMatch: '**/screenshots.spec.ts',
  projects: [
    {
      name: 'telefono',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 390, height: 844 },
        deviceScaleFactor: 2,
        hasTouch: true,
        isMobile: true,
      },
    },
  ],
})
