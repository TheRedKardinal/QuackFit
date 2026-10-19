import stili from './CoachQuack.module.css'

/** neutro: pagine di accesso; contento: vittoria; triste: sconfitta. */
export type StatoCoachQuack = 'neutro' | 'contento' | 'triste'

interface Props {
  stato?: StatoCoachQuack
  /** Lato in pixel (la figura è quadrata). */
  dimensione?: number
  className?: string
}

/**
 * Coach Quack, la mascotte: papero atletico con la fascia sportiva rossa, disegnato a mano in SVG e animato solo con
 * il CSS (nessuna immagine esterna né libreria). È decorativo (aria-hidden): accanto c'è sempre un testo.
 * Con prefers-reduced-motion le animazioni si fermano e resta la posa dello stato.
 */
function CoachQuack({ stato = 'neutro', dimensione = 120, className }: Props) {
  return (
    <svg
      viewBox="0 0 120 120"
      width={dimensione}
      height={dimensione}
      aria-hidden="true"
      focusable="false"
      data-stato={stato}
      className={`${stili.mascotte} ${stili[stato]} ${className ?? ''}`}
    >
      <ellipse className={stili.ombra} cx="60" cy="113" rx="30" ry="4" />

      <g className={stili.figura}>
        {/* zampe */}
        <ellipse className={stili.arancio} cx="47" cy="105" rx="9" ry="4.5" />
        <ellipse className={stili.arancio} cx="73" cy="105" rx="9" ry="4.5" />

        {/* ali, dietro al corpo */}
        <ellipse className={`${stili.ala} ${stili.alaSinistra}`} cx="25" cy="74" rx="9" ry="15" />
        <ellipse className={`${stili.ala} ${stili.alaDestra}`} cx="95" cy="74" rx="9" ry="15" />

        {/* corpo e pancia */}
        <ellipse className={stili.corpo} cx="60" cy="66" rx="38" ry="39" />
        <ellipse className={stili.pancia} cx="60" cy="82" rx="23" ry="17" />

        {/* fascia sportiva con il nodo a destra */}
        <rect className={stili.fascia} x="23" y="38" width="74" height="9" rx="4.5" />
        <path className={stili.fascia} d="M95 40 L108 34 L106 43 Z" />
        <path className={stili.fascia} d="M95 45 L107 50 L101 54 Z" />

        {/* guance */}
        <ellipse className={stili.guancia} cx="38" cy="70" rx="6" ry="3.5" />
        <ellipse className={stili.guancia} cx="82" cy="70" rx="6" ry="3.5" />

        {stato === 'contento' ? (
          <>
            {/* occhi a mezzaluna e becco aperto */}
            <path className={stili.occhioChiuso} d="M42 60 Q48 52 54 60" />
            <path className={stili.occhioChiuso} d="M66 60 Q72 52 78 60" />
            <path className={stili.arancio} d="M49 66 Q60 62 71 66 Q60 70 49 66 Z" />
            <path className={stili.boccaAperta} d="M51 68 Q60 79 69 68 Z" />
          </>
        ) : (
          <>
            {/* occhi aperti con il riflesso */}
            <g className={stili.occhi}>
              <circle className={stili.pupilla} cx="48" cy="58" r="5" />
              <circle className={stili.pupilla} cx="72" cy="58" r="5" />
              <circle className={stili.riflesso} cx="49.6" cy="56.4" r="1.6" />
              <circle className={stili.riflesso} cx="73.6" cy="56.4" r="1.6" />
            </g>
            {stato === 'triste' ? (
              <>
                {/* sopracciglia alzate al centro e becco all'ingiù */}
                <path className={stili.sopracciglio} d="M41 52 L53 49" />
                <path className={stili.sopracciglio} d="M79 52 L67 49" />
                <path className={stili.arancio} d="M50 70 Q60 64 70 70 Q60 73 50 70 Z" />
                {/* goccia di sudore */}
                <path className={stili.goccia} d="M88 50 Q84 57 88 59 Q92 57 88 50 Z" />
              </>
            ) : (
              <ellipse className={stili.arancio} cx="60" cy="67" rx="10" ry="5.5" />
            )}
          </>
        )}
      </g>
    </svg>
  )
}

export default CoachQuack
