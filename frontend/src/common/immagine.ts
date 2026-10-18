/**
 * Preparazione della foto profilo nel browser (frontend/CLAUDE.md, Profilo → Avatar): quadrato centrale portato a
 * 512×512 (mai ingrandito) e compresso in JPEG qualità 0,85, così resta molto sotto i 2 MB del backend.
 */

export const LATO_AVATAR = 512
const QUALITA_JPEG = 0.85
/** Limite del backend (me.md, Foto profilo): 2 × 1024 × 1024 byte. */
export const MASSIMO_BYTE_AVATAR = 2 * 1024 * 1024

export interface Ritaglio {
  /** Angolo in alto a sinistra del quadrato da prendere dall'originale. */
  x: number
  y: number
  /** Lato del quadrato nell'originale. */
  lato: number
  /** Lato dell'immagine finale (LATO_AVATAR, o meno se l'originale è più piccolo). */
  latoFinale: number
}

/** Il quadrato centrale: lato = il lato più corto dell'originale, centrato sull'altro. */
export function calcolaRitaglio(larghezza: number, altezza: number, latoMassimo = LATO_AVATAR): Ritaglio {
  const lato = Math.min(larghezza, altezza)
  return {
    x: Math.floor((larghezza - lato) / 2),
    y: Math.floor((altezza - lato) / 2),
    lato,
    latoFinale: Math.min(lato, latoMassimo),
  }
}

/** La foto scelta non si può usare: il browser non sa leggerla (es. HEIC su Chrome) o il risultato è troppo grande. */
export class ImmagineNonValidaError extends Error {
  constructor(messaggio: string) {
    super(messaggio)
    this.name = 'ImmagineNonValidaError'
  }
}

/**
 * Ritaglia e comprime la foto. createImageBitmap applica anche l'orientamento EXIF delle foto del telefono.
 * Lo sfondo bianco serve ai PNG trasparenti, perché il JPEG non ha trasparenza.
 */
export async function preparaAvatar(file: File): Promise<Blob> {
  let immagine: ImageBitmap
  try {
    immagine = await createImageBitmap(file)
  } catch {
    throw new ImmagineNonValidaError('Questa immagine non si può leggere: scegli una foto jpg, png o webp.')
  }
  try {
    const ritaglio = calcolaRitaglio(immagine.width, immagine.height)
    const canvas = document.createElement('canvas')
    canvas.width = ritaglio.latoFinale
    canvas.height = ritaglio.latoFinale
    const contesto = canvas.getContext('2d')
    if (!contesto) {
      throw new ImmagineNonValidaError('Il browser non riesce a preparare la foto.')
    }
    contesto.fillStyle = '#ffffff'
    contesto.fillRect(0, 0, ritaglio.latoFinale, ritaglio.latoFinale)
    contesto.drawImage(
      immagine,
      ritaglio.x,
      ritaglio.y,
      ritaglio.lato,
      ritaglio.lato,
      0,
      0,
      ritaglio.latoFinale,
      ritaglio.latoFinale,
    )
    const blob = await new Promise<Blob | null>((risolvi) => canvas.toBlob(risolvi, 'image/jpeg', QUALITA_JPEG))
    if (!blob) {
      throw new ImmagineNonValidaError('Il browser non riesce a preparare la foto.')
    }
    if (blob.size > MASSIMO_BYTE_AVATAR) {
      throw new ImmagineNonValidaError('La foto è troppo grande anche dopo la compressione.')
    }
    return blob
  } finally {
    immagine.close()
  }
}
