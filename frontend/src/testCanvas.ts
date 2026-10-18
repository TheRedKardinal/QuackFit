// Canvas e createImageBitmap finti per i test: jsdom non disegna immagini. Usati solo dai test.
import { vi } from 'vitest'

export interface CanvasFinto {
  drawImage: ReturnType<typeof vi.fn>
  fillRect: ReturnType<typeof vi.fn>
  /** Ultimo canvas creato: larghezza e altezza impostate dal codice. */
  ultimo: () => HTMLCanvasElement | undefined
}

/**
 * Sostituisce createImageBitmap (immagine con le dimensioni date) e il contesto 2d del canvas.
 * toBlob restituisce un JPEG finto della dimensione indicata (per provare il limite dei 2 MB).
 */
export function installaCanvasFinto(larghezza: number, altezza: number, byteBlob = 80_000): CanvasFinto {
  const drawImage = vi.fn()
  const fillRect = vi.fn()
  let ultimo: HTMLCanvasElement | undefined
  vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({ width: larghezza, height: altezza, close: vi.fn() }))
  const crea = document.createElement.bind(document)
  vi.spyOn(document, 'createElement').mockImplementation((tag: string, opzioni?: ElementCreationOptions) => {
    const elemento = crea(tag, opzioni)
    if (elemento instanceof HTMLCanvasElement) {
      ultimo = elemento
    }
    return elemento
  })
  const contesto = { drawImage, fillRect, fillStyle: '' }
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(
    (() => contesto) as unknown as HTMLCanvasElement['getContext'],
  )
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback: BlobCallback) => {
    callback(new Blob([new Uint8Array(byteBlob)], { type: 'image/jpeg' }))
  })
  return { drawImage, fillRect, ultimo: () => ultimo }
}

/** createImageBitmap che fallisce, come per un HEIC su Chrome. */
export function installaImmagineIlleggibile(): void {
  vi.stubGlobal('createImageBitmap', vi.fn().mockRejectedValue(new DOMException('decodifica fallita')))
}
