import { resolve } from 'node:path'
import { defineConfig, devices } from '@playwright/test'
import { FILE_LOG_BACKEND, PORTA_BACKEND, PORTA_FRONTEND, VALORI_PROVA } from './e2e/ambiente'

/**
 * Test end to end (task 51b): Playwright avvia lo stack locale completo su porte diverse da quelle di sviluppo
 * (backend 8081, Vite 5174), così non si scontra con backend e frontend già accesi.
 * Il backend usa il database dedicato quackfit_e2e (creato vuoto una volta) e scrive le email nel log su file, da cui
 * le prove leggono i codici di verifica. Utente e password del database restano quelli di backend/env.properties, che
 * qui non si legge mai: si sovrascrivono solo le variabili delle prove (le variabili d'ambiente vincono sul file).
 */
const CARTELLA_BACKEND = resolve(import.meta.dirname, '../backend')

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  // una prova alla volta: il backend ha un limite di 20 richieste al minuto per IP su /api/auth
  workers: 1,
  fullyParallel: false,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://localhost:${PORTA_FRONTEND}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'telefono',
      use: { ...devices['Desktop Chrome'], viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true },
    },
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
  ],
  webServer: [
    {
      // percorso completo del wrapper: la shell di Windows non cerca i comandi nella cartella di lavoro
      command:
        process.platform === 'win32'
          ? `"${resolve(CARTELLA_BACKEND, 'mvnw.cmd')}" spring-boot:run`
          : `"${resolve(CARTELLA_BACKEND, 'mvnw')}" spring-boot:run`,
      cwd: CARTELLA_BACKEND,
      // pronto quando /api/me risponde (401 senza token)
      url: `http://localhost:${PORTA_BACKEND}/api/me`,
      timeout: 180_000,
      reuseExistingServer: false,
      env: {
        SERVER_PORT: String(PORTA_BACKEND),
        DB_URL: VALORI_PROVA.E2E_DB_URL || 'jdbc:postgresql://localhost:5432/quackfit_e2e',
        FRONTEND_URL: `http://localhost:${PORTA_FRONTEND}`,
        MAIL_MODALITA: 'log',
        LOGGING_FILE_NAME: FILE_LOG_BACKEND,
        SUPERADMIN_EMAIL: VALORI_PROVA.E2E_SUPERADMIN_EMAIL,
        SUPERADMIN_PASSWORD: VALORI_PROVA.E2E_SUPERADMIN_PASSWORD,
      },
    },
    {
      command: `npm run dev -- --port ${PORTA_FRONTEND} --strictPort`,
      url: `http://localhost:${PORTA_FRONTEND}`,
      timeout: 60_000,
      reuseExistingServer: false,
      env: { VITE_API_URL: `http://localhost:${PORTA_BACKEND}` },
    },
  ],
})
