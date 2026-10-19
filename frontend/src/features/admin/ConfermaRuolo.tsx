import Drawer from '../../common/Drawer'
import Pulsante from '../../common/Pulsante'
import TestoPulsante from '../../common/TestoPulsante'
import type { UtenteAdmin } from './tipi'
import stili from './admin.module.css'

interface Props {
  /** Utente da modificare; null = pannello chiuso. */
  utente: UtenteAdmin | null
  azione: 'promuovi' | 'retrocedi'
  inviando: boolean
  onConferma: () => void
  onAnnulla: () => void
}

/**
 * Conferma del cambio ruolo nel Drawer (mai window.confirm): il cambio fa rifare il login all'utente modificato.
 * Verde per la promozione, rosso per la retrocessione. Durante l'invio Conferma è disattivato e Esc non chiude.
 */
function ConfermaRuolo({ utente, azione, inviando, onConferma, onAnnulla }: Props) {
  const nome = utente ? `${utente.nome} ${utente.cognome}` : ''
  const domanda =
    azione === 'promuovi'
      ? `Promuovere ${nome} ad ADMIN? Dovrà rifare il login.`
      : `Togliere il ruolo ADMIN a ${nome}? Dovrà rifare il login.`
  return (
    <Drawer
      aperto={utente !== null}
      tono={azione === 'promuovi' ? 'successo' : 'errore'}
      titolo="Cambio ruolo"
      onChiudi={inviando ? undefined : onAnnulla}
    >
      <p>{domanda}</p>
      <div className={stili.azioniConferma}>
        <Pulsante variante={azione === 'promuovi' ? 'successo' : 'pericolo'} onClick={onConferma} disabled={inviando}>
          <TestoPulsante inviando={inviando} secondiAttesa={0} testo="Conferma" />
        </Pulsante>
        <Pulsante variante="neutro" onClick={onAnnulla} disabled={inviando}>
          Annulla
        </Pulsante>
      </div>
    </Drawer>
  )
}

export default ConfermaRuolo
