import { useEffect, useState } from 'react'
import { isDatiErrore } from '../../api/ApiError'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import { useInvioForm } from '../../common/useInvioForm'
import type { DatiContenuto } from '../../common/validazione'
import { useAppDispatch } from '../../store/hooks'
import {
  cancellaLezione,
  cancellaTappa,
  caricaTappe,
  creaLezione,
  creaTappa,
  modificaLezione,
  modificaTappa,
} from './adminThunk'
import ConfermaCancellazione from './ConfermaCancellazione'
import FormContenuto from './FormContenuto'
import type { LezioneAdmin, TappaAdmin } from './tipi'
import stili from './admin.module.css'

/** Il form in linea aperto (uno alla volta). */
type FormAperto =
  | { tipo: 'nuovaTappa' }
  | { tipo: 'modificaTappa'; id: string }
  | { tipo: 'nuovaLezione'; tappaId: string }
  | { tipo: 'modificaLezione'; id: string }

/** Cancellazione in attesa di conferma. */
type DaCancellare = { tipo: 'tappa'; tappa: TappaAdmin } | { tipo: 'lezione'; lezione: LezioneAdmin }

function domandaCancellazione(elemento: DaCancellare): string {
  if (elemento.tipo === 'tappa') {
    const { titolo, lezioni } = elemento.tappa
    if (lezioni.length === 0) {
      return `Eliminare la tappa «${titolo}»?`
    }
    const quante = lezioni.length === 1 ? 'la sua lezione' : `le sue ${lezioni.length} lezioni`
    return `Eliminare la tappa «${titolo}»? Verranno eliminate anche ${quante} e le loro domande.`
  }
  const { titolo, numeroDomande } = elemento.lezione
  if (numeroDomande === 0) {
    return `Eliminare la lezione «${titolo}»?`
  }
  const quante = numeroDomande === 1 ? 'la sua domanda' : `le sue ${numeroDomande} domande`
  return `Eliminare la lezione «${titolo}»? Verranno eliminate anche ${quante}.`
}

function contaLezioni(n: number): string {
  return n === 1 ? '1 lezione' : `${n} lezioni`
}

function contaDomande(n: number): string {
  return n === 1 ? '1 domanda' : `${n} domande`
}

/**
 * Gestione di tappe e lezioni (admin.md, Ordine, Tappe, Lezioni): accordion con una tappa aperta alla volta,
 * form in linea per creare e modificare, riordino con Su/Giù, cancellazione con conferma.
 * Dopo ogni modifica si rilegge l'elenco: creazione, cancellazione e riordino cambiano anche gli altri elementi.
 * La tappa aperta si ricorda per id, così resta la stessa dopo un riordino; se sparisce, si apre la prima.
 */
function PaginaContenuti() {
  const dispatch = useAppDispatch()
  /** Aumenta per rileggere l'elenco. */
  const [ricarica, setRicarica] = useState(0)
  const [tappe, setTappe] = useState<TappaAdmin[] | null>(null)
  const [ricaricaArrivata, setRicaricaArrivata] = useState<number | null>(null)
  const [erroreCaricamento, setErroreCaricamento] = useState<string | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  const [idAperta, setIdAperta] = useState<string | null>(null)
  const [form, setForm] = useState<FormAperto | null>(null)
  const [daCancellare, setDaCancellare] = useState<DaCancellare | null>(null)
  const riordino = useInvioForm()
  const cancellazione = useInvioForm()

  const caricamento = ricaricaArrivata !== ricarica

  useEffect(() => {
    let valida = true
    dispatch(caricaTappe())
      .unwrap()
      .then((dati) => {
        if (valida) {
          setTappe(dati)
          setErroreCaricamento(null)
          setRicaricaArrivata(ricarica)
        }
      })
      .catch((dati: { codice: string }) => {
        if (valida) {
          setErroreCaricamento(dati.codice)
          setRicaricaArrivata(ricarica)
        }
      })
    return () => {
      valida = false
    }
  }, [dispatch, ricarica])

  function rileggi() {
    setRicarica((n) => n + 1)
  }

  /** La tappa aperta: quella ricordata se esiste ancora, altrimenti la prima. */
  const aperta = tappe?.find((tappa) => tappa.id === idAperta)?.id ?? tappe?.[0]?.id ?? null

  /**
   * Salvataggio di un form: se riesce chiude il form e rilegge l'elenco. Un 404 (elemento cancellato nel frattempo
   * da un altro admin) chiude il form, mostra il messaggio e rilegge; gli altri errori tornano al form.
   */
  async function salva(operazione: () => Promise<{ id: string } | undefined>, apri?: 'nuova') {
    try {
      const risposta = await operazione()
      if (apri && risposta) {
        setIdAperta(risposta.id)
      }
      setForm(null)
      rileggi()
    } catch (motivo) {
      if (isDatiErrore(motivo) && motivo.codice === 'NON_TROVATO') {
        setForm(null)
        setErrore(motivo.codice)
        rileggi()
        return
      }
      throw motivo
    }
  }

  function apriForm(nuovo: FormAperto) {
    setErrore(null)
    setForm(nuovo)
  }

  async function sposta(azione: () => Promise<unknown>) {
    setErrore(null)
    const risultato = await riordino.invia(azione)
    if (risultato) {
      // 404 o posizione non più valida: la pagina non era aggiornata
      setErrore(risultato.codice)
    }
    rileggi()
  }

  async function confermaCancellazione() {
    if (!daCancellare) {
      return
    }
    const elemento = daCancellare
    const risultato = await cancellazione.invia(() =>
      elemento.tipo === 'tappa'
        ? dispatch(cancellaTappa(elemento.tappa.id)).unwrap()
        : dispatch(cancellaLezione(elemento.lezione.id)).unwrap(),
    )
    setDaCancellare(null)
    setErrore(risultato ? risultato.codice : null)
    rileggi()
  }

  const bloccato = riordino.inviando || caricamento

  return (
    <section>
      <div className={stili.intestazionePagina}>
        <div>
          <h1 className={stili.titolo}>Gestione contenuti</h1>
          <p className={stili.sottotitolo}>Tappe e lezioni del percorso, in ordine</p>
        </div>
        <Pulsante onClick={() => apriForm({ tipo: 'nuovaTappa' })} disabled={form?.tipo === 'nuovaTappa'}>
          + Nuova tappa
        </Pulsante>
      </div>

      <MessaggioErrore codice={errore ?? erroreCaricamento ?? undefined} />

      {form?.tipo === 'nuovaTappa' && (
        <Scheda>
          <FormContenuto
            id="nuova-tappa"
            intestazione="Nuova tappa"
            testoInvio="Crea tappa"
            onSalva={(dati: DatiContenuto) => salva(() => dispatch(creaTappa(dati)).unwrap(), 'nuova')}
            onAnnulla={() => setForm(null)}
          />
        </Scheda>
      )}

      {!tappe && caricamento && <p>Caricamento…</p>}
      {tappe && tappe.length === 0 && <p>Nessuna tappa: crea la prima con "+ Nuova tappa".</p>}

      <ol className={stili.elencoTappe}>
        {tappe?.map((tappa, indice) => {
          const espansa = tappa.id === aperta
          const idCorpo = `tappa-${tappa.id}-lezioni`
          return (
            <li key={tappa.id}>
              <Scheda className={stili.tappa}>
                {form?.tipo === 'modificaTappa' && form.id === tappa.id ? (
                  <FormContenuto
                    id={`tappa-${tappa.id}`}
                    intestazione={`Modifica la tappa «${tappa.titolo}»`}
                    iniziale={{ titolo: tappa.titolo, descrizione: tappa.descrizione }}
                    testoInvio="Salva"
                    onSalva={(dati) => salva(() => dispatch(modificaTappa({ id: tappa.id, dati })).unwrap())}
                    onAnnulla={() => setForm(null)}
                  />
                ) : (
                  <>
                    <button
                      type="button"
                      className={stili.intestazioneTappa}
                      aria-expanded={espansa}
                      aria-controls={idCorpo}
                      onClick={() => setIdAperta(tappa.id)}
                    >
                      <span className={stili.numeroTappa} aria-hidden="true">
                        {tappa.ordine}
                      </span>
                      <span className={stili.testiTappa}>
                        <span className={stili.titoloTappa}>
                          <span className="visually-hidden">Tappa {tappa.ordine}: </span>
                          {tappa.titolo}
                        </span>
                        <span className={stili.descrizioneTappa}>{tappa.descrizione}</span>
                      </span>
                      <span className={stili.conteggio}>{contaLezioni(tappa.lezioni.length)}</span>
                      <span className={stili.freccia} aria-hidden="true">
                        {espansa ? '▾' : '▸'}
                      </span>
                    </button>
                    <div className={stili.azioniElemento}>
                      <Pulsante
                        variante="neutro"
                        aria-label={`Sposta su la tappa «${tappa.titolo}»`}
                        disabled={indice === 0 || bloccato}
                        onClick={() =>
                          sposta(() => dispatch(modificaTappa({ id: tappa.id, dati: { ordine: tappa.ordine - 1 } })).unwrap())
                        }
                      >
                        ↑ Su
                      </Pulsante>
                      <Pulsante
                        variante="neutro"
                        aria-label={`Sposta giù la tappa «${tappa.titolo}»`}
                        disabled={indice === tappe.length - 1 || bloccato}
                        onClick={() =>
                          sposta(() => dispatch(modificaTappa({ id: tappa.id, dati: { ordine: tappa.ordine + 1 } })).unwrap())
                        }
                      >
                        ↓ Giù
                      </Pulsante>
                      <Pulsante
                        variante="neutro"
                        aria-label={`Modifica la tappa «${tappa.titolo}»`}
                        onClick={() => apriForm({ tipo: 'modificaTappa', id: tappa.id })}
                      >
                        Modifica
                      </Pulsante>
                      <Pulsante
                        variante="pericolo"
                        aria-label={`Elimina la tappa «${tappa.titolo}»`}
                        onClick={() => setDaCancellare({ tipo: 'tappa', tappa })}
                      >
                        Elimina
                      </Pulsante>
                    </div>
                  </>
                )}

                {espansa && (
                  <div id={idCorpo} className={stili.corpoTappa}>
                    <p className={stili.etichettaSezione}>Lezioni</p>
                    {tappa.lezioni.length === 0 && <p>Nessuna lezione in questa tappa.</p>}
                    <ol className={stili.elencoLezioni}>
                      {tappa.lezioni.map((lezione, indiceLezione) => (
                        <li key={lezione.id} className={stili.lezione}>
                          {form?.tipo === 'modificaLezione' && form.id === lezione.id ? (
                            <FormContenuto
                              id={`lezione-${lezione.id}`}
                              intestazione={`Modifica la lezione «${lezione.titolo}»`}
                              iniziale={{ titolo: lezione.titolo, descrizione: lezione.descrizione }}
                              testoInvio="Salva"
                              onSalva={(dati) => salva(() => dispatch(modificaLezione({ id: lezione.id, dati })).unwrap())}
                              onAnnulla={() => setForm(null)}
                            />
                          ) : (
                            <>
                              <div className={stili.testiLezione}>
                                <span className={stili.titoloLezione}>
                                  L{lezione.ordine} · {lezione.titolo}
                                </span>
                                <span className={stili.descrizioneTappa}>{lezione.descrizione}</span>
                                <span className={stili.conteggio}>{contaDomande(lezione.numeroDomande)}</span>
                              </div>
                              <div className={stili.azioniElemento}>
                                <Pulsante
                                  variante="neutro"
                                  aria-label={`Sposta su la lezione «${lezione.titolo}»`}
                                  disabled={indiceLezione === 0 || bloccato}
                                  onClick={() =>
                                    sposta(() =>
                                      dispatch(
                                        modificaLezione({ id: lezione.id, dati: { ordine: lezione.ordine - 1 } }),
                                      ).unwrap(),
                                    )
                                  }
                                >
                                  ↑ Su
                                </Pulsante>
                                <Pulsante
                                  variante="neutro"
                                  aria-label={`Sposta giù la lezione «${lezione.titolo}»`}
                                  disabled={indiceLezione === tappa.lezioni.length - 1 || bloccato}
                                  onClick={() =>
                                    sposta(() =>
                                      dispatch(
                                        modificaLezione({ id: lezione.id, dati: { ordine: lezione.ordine + 1 } }),
                                      ).unwrap(),
                                    )
                                  }
                                >
                                  ↓ Giù
                                </Pulsante>
                                <Pulsante
                                  variante="neutro"
                                  aria-label={`Modifica la lezione «${lezione.titolo}»`}
                                  onClick={() => apriForm({ tipo: 'modificaLezione', id: lezione.id })}
                                >
                                  Modifica
                                </Pulsante>
                                <Pulsante
                                  variante="pericolo"
                                  aria-label={`Elimina la lezione «${lezione.titolo}»`}
                                  onClick={() => setDaCancellare({ tipo: 'lezione', lezione })}
                                >
                                  Elimina
                                </Pulsante>
                              </div>
                            </>
                          )}
                        </li>
                      ))}
                    </ol>
                    {form?.tipo === 'nuovaLezione' && form.tappaId === tappa.id ? (
                      <FormContenuto
                        id={`nuova-lezione-${tappa.id}`}
                        intestazione={`Nuova lezione in «${tappa.titolo}»`}
                        testoInvio="Crea lezione"
                        onSalva={(dati) => salva(() => dispatch(creaLezione({ tappaId: tappa.id, dati })).unwrap())}
                        onAnnulla={() => setForm(null)}
                      />
                    ) : (
                      <Pulsante
                        variante="neutro"
                        larghezzaPiena
                        onClick={() => apriForm({ tipo: 'nuovaLezione', tappaId: tappa.id })}
                      >
                        + Aggiungi lezione
                      </Pulsante>
                    )}
                  </div>
                )}
              </Scheda>
            </li>
          )
        })}
      </ol>

      <ConfermaCancellazione
        domanda={daCancellare ? domandaCancellazione(daCancellare) : null}
        inviando={cancellazione.inviando}
        onConferma={confermaCancellazione}
        onAnnulla={() => setDaCancellare(null)}
      />
    </section>
  )
}

export default PaginaContenuti
