/**
 * Icone disegnate a mano in SVG (nessuna libreria). Sono sempre decorative (aria-hidden): accanto c'è sempre un testo
 * o un'etichetta accessibile. Il colore è quello del testo (currentColor).
 */
export type NomeIcona =
  | 'percorso'
  | 'profilo'
  | 'fiamma'
  | 'admin'
  | 'utenti'
  | 'contenuti'
  | 'indietro'
  | 'spunta'
  | 'lucchetto'
  | 'manubrio'

const TRACCIATI: Record<NomeIcona, string> = {
  // sentiero che sale a zig-zag con una bandierina in cima
  percorso: 'M6 21c0-3 4-3 4-6s-4-3-4-6 4-3 6-3M12 3v5M12 3l5 1.5L12 6',
  // testa e spalle in un cerchio
  profilo: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 12a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6.5 18.5c1.2-2 3.2-3 5.5-3s4.3 1 5.5 3',
  // fiamma della streak
  fiamma: 'M12 21c-3.9 0-6.5-2.6-6.5-6.2 0-3.4 2.3-5.4 3.8-7.8.4 1.6 1.2 2.8 2.3 3.4.3-2.6 1.4-5.2 3.4-7.4.2 3 1.4 4.7 2.6 6.4 1 1.4 1.9 2.8 1.9 5.2C19.5 18.4 16 21 12 21z',
  // ingranaggio semplificato
  admin: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1',
  // due persone: una davanti e una dietro
  utenti: 'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20c.6-3.4 3.2-5.5 6.5-5.5s5.9 2.1 6.5 5.5M16 4.3a3.5 3.5 0 0 1 0 6.4M18 14.8c1.9.8 3.1 2.6 3.5 5.2',
  // libro aperto
  contenuti: 'M12 6.5C10.3 5 7.8 4.5 3 4.5v14c4.8 0 7.3.5 9 2 1.7-1.5 4.2-2 9-2v-14c-4.8 0-7.3.5-9 2zM12 6.5v14',
  // freccia verso sinistra
  indietro: 'M19 12H5M11 6l-6 6 6 6',
  // segno di spunta
  spunta: 'M5 12.5l4.5 4.5L19 7.5',
  // lucchetto chiuso
  lucchetto: 'M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3M12 15v2',
  // manubrio da palestra
  manubrio: 'M6 7v10M3 9.5v5M18 7v10M21 9.5v5M6 12h12',
}

interface Props {
  nome: NomeIcona
  dimensione?: number
}

function Icona({ nome, dimensione = 24 }: Props) {
  return (
    <svg
      width={dimensione}
      height={dimensione}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d={TRACCIATI[nome]} />
    </svg>
  )
}

export default Icona
