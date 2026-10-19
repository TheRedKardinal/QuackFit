import type { TipoDomanda } from './tipi'

/** Nome di ogni tipo di domanda nell'interfaccia admin. */
export const NOMI_TIPO: Record<TipoDomanda, string> = {
  VERO_FALSO: 'Vero o falso',
  SCELTA_MULTIPLA: 'Scelta multipla',
  ABBINAMENTO: 'Abbinamento',
}
