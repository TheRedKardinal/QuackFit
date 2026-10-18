import { afterEach, describe, expect, it, vi } from 'vitest'
import { installaCanvasFinto, installaImmagineIlleggibile } from '../testCanvas'
import { calcolaRitaglio, ImmagineNonValidaError, MASSIMO_BYTE_AVATAR, preparaAvatar } from './immagine'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

const FILE = new File(['x'], 'foto.jpg', { type: 'image/jpeg' })

describe('calcolaRitaglio', () => {
  it('foto orizzontale: quadrato centrale dal lato corto, ridotto a 512', () => {
    expect(calcolaRitaglio(4000, 3000)).toEqual({ x: 500, y: 0, lato: 3000, latoFinale: 512 })
  })

  it('foto verticale: quadrato centrale in altezza', () => {
    expect(calcolaRitaglio(1080, 1920)).toEqual({ x: 0, y: 420, lato: 1080, latoFinale: 512 })
  })

  it('foto già quadrata', () => {
    expect(calcolaRitaglio(800, 800)).toEqual({ x: 0, y: 0, lato: 800, latoFinale: 512 })
  })

  it('foto piccola: mai ingrandita', () => {
    expect(calcolaRitaglio(300, 200)).toEqual({ x: 50, y: 0, lato: 200, latoFinale: 200 })
  })

  it('lato dispari: niente mezzi pixel', () => {
    expect(calcolaRitaglio(1001, 600)).toEqual({ x: 200, y: 0, lato: 600, latoFinale: 512 })
  })
})

describe('preparaAvatar', () => {
  it('disegna il quadrato centrale su un canvas 512×512 con lo sfondo bianco e restituisce un JPEG', async () => {
    const canvas = installaCanvasFinto(4000, 3000)

    const foto = await preparaAvatar(FILE)

    expect(foto.type).toBe('image/jpeg')
    expect(canvas.ultimo()?.width).toBe(512)
    expect(canvas.ultimo()?.height).toBe(512)
    expect(canvas.fillRect).toHaveBeenCalledWith(0, 0, 512, 512)
    expect(canvas.drawImage).toHaveBeenCalledWith(expect.anything(), 500, 0, 3000, 3000, 0, 0, 512, 512)
    expect(HTMLCanvasElement.prototype.toBlob).toHaveBeenCalledWith(expect.any(Function), 'image/jpeg', 0.85)
  })

  it('immagine che il browser non sa leggere: errore con un messaggio per l\'utente', async () => {
    installaImmagineIlleggibile()

    await expect(preparaAvatar(FILE)).rejects.toBeInstanceOf(ImmagineNonValidaError)
  })

  it('oltre i 2 MB anche dopo la compressione: errore', async () => {
    installaCanvasFinto(4000, 3000, MASSIMO_BYTE_AVATAR + 1)

    await expect(preparaAvatar(FILE)).rejects.toThrow('troppo grande')
  })
})
