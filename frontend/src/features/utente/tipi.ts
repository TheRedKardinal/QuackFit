// Tipi degli endpoint di /api/me (backend/docs/specs/me.md): campi identici al JSON del backend.

export interface ModificaDatiRequest {
  nome: string
  cognome: string
}

export interface CambioPasswordRequest {
  passwordAttuale: string
  nuovaPassword: string
}

/** Risposta di PATCH /api/me/avatar: l'URL https della nuova foto. */
export interface AvatarResponse {
  fotoProfilo: string
}

export interface CambioEmailRequest {
  nuovaEmail: string
  password: string
}

export interface ConfermaCambioEmailRequest {
  codice: string
}

export interface CancellazioneAccountRequest {
  password: string
}
