import { useEffect, useState, type FormEvent } from 'react'
import FotoProfilo from '../../common/FotoProfilo'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import { useInvioForm } from '../../common/useInvioForm'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { caricaUtenti, cambiaRuolo } from './adminThunk'
import { azioneRuolo, eSeStesso, type AzioneRuolo } from './azioneRuolo'
import ConfermaRuolo from './ConfermaRuolo'
import type { Pagina, UtenteAdmin } from './tipi'
import stili from './admin.module.css'

/** Cambio di ruolo in attesa di conferma. */
interface CambioDaConfermare {
  utente: UtenteAdmin
  azione: 'promuovi' | 'retrocedi'
}

const ETICHETTE_RUOLO = { UTENTE: 'Utente', ADMIN: 'Admin', SUPERADMIN: 'Superadmin' } as const

function data(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT')
}

function EtichettaRuolo({ utente }: { utente: UtenteAdmin }) {
  return <span className={`${stili.ruolo} ${stili[`ruolo${utente.ruolo}`]}`}>{ETICHETTE_RUOLO[utente.ruolo]}</span>
}

/** Email verificata: icona e testo, non solo il colore. */
function Verificata({ si }: { si: boolean }) {
  return (
    <span className={si ? stili.verificata : stili.nonVerificata}>
      <span aria-hidden="true">{si ? '✓' : '✗'}</span> {si ? 'Sì' : 'No'}
    </span>
  )
}

interface PropsAzione {
  azione: AzioneRuolo
  onScegli: (azione: 'promuovi' | 'retrocedi') => void
}

/** Il pulsante dell'azione consentita, oppure il motivo per cui non ce n'è. */
function Azione({ azione, onScegli }: PropsAzione) {
  if (azione.tipo === 'nessuna') {
    return <span className={stili.motivo}>{azione.motivo}</span>
  }
  return azione.tipo === 'promuovi' ? (
    <Pulsante variante="neutro" onClick={() => onScegli('promuovi')}>
      Promuovi ad ADMIN
    </Pulsante>
  ) : (
    <Pulsante variante="neutro" onClick={() => onScegli('retrocedi')}>
      Retrocedi a UTENTE
    </Pulsante>
  )
}

/**
 * Gestione utenti (admin.md, Utenti): tabella su desktop, card su mobile, ricerca q con Invio o "Cerca", paginazione
 * Precedente/Successivo (size di default del backend, 20) e cambio ruolo con conferma.
 * La lista vive solo qui: stato locale e thunk della feature, come in features/utente.
 */
function PaginaUtenti() {
  const dispatch = useAppDispatch()
  const chiamante = useAppSelector((stato) => stato.sessione.utente)

  const [testoRicerca, setTestoRicerca] = useState('')
  const [q, setQ] = useState('')
  const [pagina, setPagina] = useState(0)
  /** Aumenta per ricaricare la stessa pagina (dopo un cambio ruolo o un 403). */
  const [ricarica, setRicarica] = useState(0)
  /** Ultima pagina arrivata dal backend (resta visibile mentre arriva la prossima). */
  const [risultato, setRisultato] = useState<Pagina<UtenteAdmin> | null>(null)
  /** Chiave della richiesta a cui si riferisce l'ultima risposta (riuscita o no). */
  const [chiaveArrivata, setChiaveArrivata] = useState<string | null>(null)
  const [erroreCaricamento, setErroreCaricamento] = useState<string | null>(null)
  const [erroreAzione, setErroreAzione] = useState<string | null>(null)
  const [daConfermare, setDaConfermare] = useState<CambioDaConfermare | null>(null)
  const [esito, setEsito] = useState<string | null>(null)
  const { invia, inviando } = useInvioForm()

  // La richiesta corrente; "in caricamento" finché non arriva la sua risposta (niente setState sincrono nell'effetto)
  const chiave = `${pagina}|${q}|${ricarica}`
  const caricamento = chiaveArrivata !== chiave

  useEffect(() => {
    // Se pagina o ricerca cambiano prima della risposta, la risposta vecchia si ignora
    let valida = true
    dispatch(caricaUtenti({ pagina, q }))
      .unwrap()
      .then((dati) => {
        if (valida) {
          setRisultato(dati)
          setErroreCaricamento(null)
          setChiaveArrivata(chiave)
        }
      })
      .catch((dati: { codice: string }) => {
        if (valida) {
          setErroreCaricamento(dati.codice)
          setChiaveArrivata(chiave)
        }
      })
    return () => {
      valida = false
    }
  }, [dispatch, pagina, q, chiave])

  if (!chiamante) {
    return null
  }

  function cerca(evento: FormEvent) {
    evento.preventDefault()
    setEsito(null)
    setErroreAzione(null)
    setQ(testoRicerca.trim())
    setPagina(0)
    setRicarica((n) => n + 1)
  }

  function pulisci() {
    setEsito(null)
    setErroreAzione(null)
    setTestoRicerca('')
    setQ('')
    setPagina(0)
    setRicarica((n) => n + 1)
  }

  async function conferma() {
    if (!daConfermare) {
      return
    }
    const { utente, azione } = daConfermare
    const erroreInvio = await invia(() =>
      dispatch(cambiaRuolo({ id: utente.id, ruolo: azione === 'promuovi' ? 'ADMIN' : 'UTENTE' })).unwrap(),
    )
    setDaConfermare(null)
    if (erroreInvio) {
      // 403 o 404: la pagina non era aggiornata, si ricarica per mostrare lo stato vero
      setErroreAzione(erroreInvio.codice)
      setEsito(null)
    } else {
      setErroreAzione(null)
      setEsito(`Ruolo di ${utente.nome} ${utente.cognome} aggiornato.`)
    }
    setRicarica((n) => n + 1)
  }

  function scegli(utente: UtenteAdmin) {
    return (azione: 'promuovi' | 'retrocedi') => {
      setEsito(null)
      setErroreAzione(null)
      setDaConfermare({ utente, azione })
    }
  }

  const utenti = risultato?.contenuto ?? []
  const totalePagine = risultato?.totalePagine ?? 0

  return (
    <section>
      <h1 className={stili.titolo}>Gestione utenti</h1>
      <p className={stili.sottotitolo}>Utenti attivi e ruoli di accesso</p>

      <form className={stili.ricerca} onSubmit={cerca} role="search" noValidate>
        <label htmlFor="ricerca-utenti" className="visually-hidden">
          Cerca per nome, cognome o email
        </label>
        <input
          id="ricerca-utenti"
          type="search"
          className={`form-control ${stili.campoRicerca}`}
          placeholder="Cerca per nome, cognome o email"
          value={testoRicerca}
          onChange={(evento) => setTestoRicerca(evento.target.value)}
        />
        <Pulsante type="submit">Cerca</Pulsante>
        <Pulsante variante="neutro" onClick={pulisci}>
          Pulisci
        </Pulsante>
      </form>

      <MessaggioErrore codice={erroreAzione ?? erroreCaricamento ?? undefined} />
      {/* solo se è fallito il caricamento della lista (rete o 500), non per gli errori delle azioni: come nella mappa */}
      {erroreCaricamento && !erroreAzione && !caricamento && (
        <Pulsante onClick={() => setRicarica((n) => n + 1)}>Riprova</Pulsante>
      )}
      {esito && (
        <p role="status" className={stili.esito}>
          {esito}
        </p>
      )}

      {risultato && (
        <p className={stili.riepilogo}>
          {risultato.totaleElementi} {risultato.totaleElementi === 1 ? 'utente' : 'utenti'}
          {totalePagine > 0 && ` · Pagina ${risultato.pagina + 1} di ${totalePagine}`}
        </p>
      )}

      {caricamento && !risultato && <p>Caricamento…</p>}
      {risultato && utenti.length === 0 && <p>Nessun utente trovato.</p>}

      {utenti.length > 0 && (
        <>
          {/* desktop: tabella */}
          <Scheda className={`${stili.tabellaContenitore} d-none d-lg-block`}>
            <table className={stili.tabella}>
              <thead>
                <tr>
                  <th scope="col">Nome e cognome</th>
                  <th scope="col">Email</th>
                  <th scope="col">Email verificata</th>
                  <th scope="col">Ruolo</th>
                  <th scope="col">Registrazione</th>
                  <th scope="col">Azioni</th>
                </tr>
              </thead>
              <tbody>
                {utenti.map((utente) => (
                  <tr key={utente.id}>
                    <td>
                      <span className={stili.persona}>
                        <FotoProfilo utente={{ ...utente, fotoProfilo: null }} dimensione={2.25} />
                        <span>
                          {utente.nome} {utente.cognome}
                          {eSeStesso(chiamante, utente) && <span className={stili.tu}>Tu</span>}
                        </span>
                      </span>
                    </td>
                    <td>{utente.email}</td>
                    <td>
                      <Verificata si={utente.emailVerificata} />
                    </td>
                    <td>
                      <EtichettaRuolo utente={utente} />
                    </td>
                    <td>{data(utente.creatoAt)}</td>
                    <td>
                      <Azione azione={azioneRuolo(chiamante, utente)} onScegli={scegli(utente)} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Scheda>

          {/* mobile: card */}
          <ul className={`${stili.card} d-lg-none`}>
            {utenti.map((utente) => (
              <li key={utente.id}>
                <Scheda className={stili.cardUtente}>
                  <div className={stili.intestazioneCard}>
                    <span className={stili.persona}>
                      <FotoProfilo utente={{ ...utente, fotoProfilo: null }} dimensione={2.75} />
                      <span>
                        <span className={stili.nome}>
                          {utente.nome} {utente.cognome}
                          {eSeStesso(chiamante, utente) && <span className={stili.tu}>Tu</span>}
                        </span>
                        <span className={stili.email}>
                          {utente.email.split('@')[0]}@<wbr />
                          {utente.email.split('@').slice(1).join('@')}
                        </span>
                      </span>
                    </span>
                    <EtichettaRuolo utente={utente} />
                  </div>
                  <div className={stili.dettagliCard}>
                    <span>
                      Email verificata: <Verificata si={utente.emailVerificata} />
                    </span>
                    <span>Iscritto: {data(utente.creatoAt)}</span>
                  </div>
                  <div className={stili.azioneCard}>
                    <Azione azione={azioneRuolo(chiamante, utente)} onScegli={scegli(utente)} />
                  </div>
                </Scheda>
              </li>
            ))}
          </ul>
        </>
      )}

      {totalePagine > 1 && (
        <nav className={stili.paginazione} aria-label="Pagine">
          <Pulsante variante="neutro" disabled={pagina === 0 || caricamento} onClick={() => setPagina((p) => p - 1)}>
            Precedente
          </Pulsante>
          <Pulsante
            variante="neutro"
            disabled={pagina >= totalePagine - 1 || caricamento}
            onClick={() => setPagina((p) => p + 1)}
          >
            Successivo
          </Pulsante>
        </nav>
      )}

      <ConfermaRuolo
        utente={daConfermare?.utente ?? null}
        azione={daConfermare?.azione ?? 'promuovi'}
        inviando={inviando}
        onConferma={conferma}
        onAnnulla={() => setDaConfermare(null)}
      />
    </section>
  )
}

export default PaginaUtenti
