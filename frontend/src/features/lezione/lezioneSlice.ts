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
  /** Istante di avvio (ms): il tempo mostrato a fine lezione va da qui a `fine`. */
  inizio: number | null
  /** Istante dell'esito finale: 200 di complete (vittoria) o terzo errore (sconfitta). */
  fine: number | null
  /** Streak di /api/me all'avvio: la vittoria mostra "+1 OGGI" solo se quella nuova è più alta. */
  streakPrima: number
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
  fine: null,
  streakPrima: 0,
  chiusura: null,
}

interface DatiAvvio {
  tentativoId: string
  lezioneId: string
  inizio: number
  streakPrima: number
}

function nuovoTentativo({ tentativoId, lezioneId, inizio, streakPrima }: DatiAvvio): StatoLezione {
  return { ...statoVuoto, tentativoId, lezioneId, inizio, streakPrima }
}

const lezioneSlice = createSlice({
  name: 'lezione',
  initialState: statoVuoto,
  reducers: {
    /** Tentativo appena avviato dalla mappa (tentativoId nello state del router). */
    lezioneIniziata(_stato, azione: PayloadAction<DatiAvvio>) {
      return nuovoTentativo(azione.payload)
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
        const { esito, istante } = azione.payload
        stato.errori = esito.errori
        stato.esito = esito.esito
        if (esito.esito === 'FALLITO') {
          stato.fine = istante
        }
        const inviata = azione.meta.arg
        stato.ultimaRisposta = {
          domandaId: inviata.domandaId,
          opzioneId: 'opzioneId' in inviata ? inviata.opzioneId : null,
          coppie: 'coppie' in inviata ? inviata.coppie : [],
          esito,
        }
      })
      .addCase(chiudiTentativo.fulfilled, (stato, azione) => {
        const { chiusura, istante } = azione.payload
        stato.esito = chiusura.esito
        stato.errori = chiusura.errori
        stato.chiusura = chiusura
        stato.fine = istante
      })
      .addCase(riavviaLezione.fulfilled, (_stato, azione) => nuovoTentativo(azione.payload))
  },
})

export const { lezioneIniziata, avanti } = lezioneSlice.actions
export default lezioneSlice.reducer
