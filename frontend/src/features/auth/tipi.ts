// Tipi di POST /api/auth/register e /login (backend/docs/specs/auth.md): campi identici al JSON del backend.

export interface RegistrazioneRequest {
  nome: string
  cognome: string
  email: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}

/** Risposta di registrazione e login: il solo token JWT. */
export interface TokenResponse {
  token: string
}
