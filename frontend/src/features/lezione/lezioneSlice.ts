import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import { avanza, codaIniziale, type Coda } from './codaLezione'
import { caricaContenuto, chiudiTentativo, inviaRisposta, riavviaLezione } from './lezioneThunk'
import type { ChiusuraTentativo, Coppia, DomandaLezione, EsitoRisposta, EsitoTentativo } from './tipi'

/** L'esito appena arrivato, mostrato nel drawer finché l'utente non preme "Continua". */
export interface UltimaRisposta {
  domandaId: string
  /** Opzione scelta (null per l'abbinamento). */
  opzioneId: string | null
  /** Coppie scelte (vuota per Vero/Falso e Scelta multipla). */
  coppie: Coppia[]
  esito: EsitoRisposta
}

/**
 * Tentativo in corso (frontend/CLAUDE.md, Lezione). Vive solo in memoria: ricaricando la pagina si perde e l'utente
 * riavvia la lezione dalla mappa. La scelta della prossima domanda sta in codaLezione (funzioni pure).
 */
export interface StatoLezione {
  tentativoId: string | null
  lezioneId: string | null
  titolo: string
  domande: DomandaLezione[]
  /** false finché il contenuto non è arrivato. */
  caricata: boolean
  coda: Coda
  errori: number
  esito: EsitoTentativo
  ultimaRisposta: UltimaRisposta | null
  /** Istante di avvio (ms), per il tempo impiegato mostrato a fine lezione (task 45). */
  inizio: number | null
  /** Risposta di complete: c'è solo dopo una chiusura riuscita. */
  chiusura: ChiusuraTentativo | null
}

const statoVuoto: StatoLezione = {
  tentativoId: null,
  lezioneId: null,
  titolo: '',
  domande: [],
  caricata: false,
  coda: codaIniziale([]),
  errori: 0,
  esito: 'IN_CORSO',
  ultimaRisposta: null,
  inizio: null,
  chiusura: null,
}

function nuovoTentativo(tentativoId: string, lezioneId: string, inizio: number): StatoLezione {
  return { ...statoVuoto, tentativoId, lezioneId, inizio }
}

const lezioneSlice = createSlice({
  name: 'lezione',
  initialState: statoVuoto,
  reducers: {
    /** Tentativo appena avviato dalla mappa (tentativoId nello state del router). */
    lezioneIniziata(_stato, azione: PayloadAction<{ tentativoId: string; lezioneId: string; inizio: number }>) {
      const { tentativoId, lezioneId, inizio } = azione.payload
      return nuovoTentativo(tentativoId, lezioneId, inizio)
    },
    /** "Continua" nel drawer: la domanda esce dalla coda (se sbagliata torna a fine giro). */
    avanti(stato) {
      if (stato.ultimaRisposta) {
        stato.coda = avanza(stato.coda, stato.ultimaRisposta.esito.corretta)
        stato.ultimaRisposta = null
      }
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(caricaContenuto.fulfilled, (stato, azione) => {
        stato.titolo = azione.payload.titolo
        stato.domande = azione.payload.domande
        stato.coda = codaIniziale(azione.payload.domande.map((domanda) => domanda.id))
        stato.caricata = true
      })
      .addCase(inviaRisposta.fulfilled, (stato, azione) => {
        stato.errori = azione.payload.errori
        stato.esito = azione.payload.esito
        const inviata = azione.meta.arg
        stato.ultimaRisposta = {
          domandaId: inviata.domandaId,
          opzioneId: 'opzioneId' in inviata ? inviata.opzioneId : null,
          coppie: 'coppie' in inviata ? inviata.coppie : [],
          esito: azione.payload,
        }
      })
      .addCase(chiudiTentativo.fulfilled, (stato, azione) => {
        stato.esito = azione.payload.esito
        stato.errori = azione.payload.errori
        stato.chiusura = azione.payload
      })
      .addCase(riavviaLezione.fulfilled, (_stato, azione) => {
        const { tentativoId, lezioneId, inizio } = azione.payload
        return nuovoTentativo(tentativoId, lezioneId, inizio)
      })
  },
})

export const { lezioneIniziata, avanti } = lezioneSlice.actions
export default lezioneSlice.reducer
