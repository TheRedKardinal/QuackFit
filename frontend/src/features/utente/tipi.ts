// Tipi di PATCH /api/me e /api/me/password (backend/docs/specs/me.md): campi identici al JSON del backend.

export interface ModificaDatiRequest {
  nome: string
  cognome: string
}

export interface CambioPasswordRequest {
  passwordAttuale: string
  nuovaPassword: string
}
