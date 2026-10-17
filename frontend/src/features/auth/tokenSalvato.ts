// Token JWT in localStorage (frontend/CLAUDE.md, Sicurezza). Solo questo file tocca la chiave.
const CHIAVE = 'quackfit.token'

export function leggiToken(): string | null {
  return localStorage.getItem(CHIAVE)
}

export function salvaToken(token: string): void {
  localStorage.setItem(CHIAVE, token)
}

export function cancellaToken(): void {
  localStorage.removeItem(CHIAVE)
}
