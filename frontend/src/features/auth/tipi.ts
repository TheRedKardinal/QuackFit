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

export interface VerificaEmailRequest {
  codice: string
}

export interface PasswordDimenticataRequest {
  email: string
}

export interface ReimpostaPasswordRequest {
  email: string
  codice: string
  nuovaPassword: string
}

/** Risposta della password dimenticata: sempre lo stesso messaggio generico. */
export interface MessaggioResponse {
  messaggio: string
}

/** State del router dalla reimpostazione al login: solo messaggio ed email, mai codice o password. */
export interface StatoVersoLogin {
  messaggio: string
  email: string
}
