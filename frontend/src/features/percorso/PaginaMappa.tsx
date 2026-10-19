import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { isDatiErrore } from '../../api/ApiError'
import Icona from '../../common/Icona'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import { useAppDispatch } from '../../store/hooks'
import { avviaLezione, caricaMappa } from './percorsoThunk'
import type { LezionePercorso, StatoLezione, TappaPercorso } from './tipi'
import stili from './mappa.module.css'

/** Lo stato scritto anche a parole: non si affida al solo colore. */
const TESTO_STATO: Record<StatoLezione, string> = {
  COMPLETATA: 'completata',
  SBLOCCATA: 'da fare',
  BLOCCATA: 'bloccata',
}

/** Avviso sopra la mappa dopo un avvio non riuscito. */
type Avviso = { tipo: 'inArrivo' } | { tipo: 'errore'; codice: string }

/** Spostamento orizzontale dei nodi, per il percorso a zig-zag. */
const ZIG_ZAG = [stili.centro, stili.destra, stili.centro, stili.sinistra]

/**
 * Mappa del percorso (percorso.md, Mappa): tutte le tappe in verticale, con i nodi delle lezioni nei tre stati
 * calcolati dal backend (verde con spunta, gialla da fare, grigia con lucchetto). All'apertura scorre fino al nodo
 * da fare. Il tocco su una lezione la avvia (POST /api/attempts) e porta alla lezione; una bloccata non si tocca.
 * Avvio con 404 (lezione senza domande, percorso.md, Lezioni senza domande): riquadro "Lezione in arrivo" e mappa
 * riletta; con 403 LEZIONE_BLOCCATA (mappa non aggiornata) messaggio e mappa riletta.
 */
function PaginaMappa() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  /** Aumenta per rileggere la mappa. */
  const [ricarica, setRicarica] = useState(0)
  const [ricaricaArrivata, setRicaricaArrivata] = useState<number | null>(null)
  const [tappe, setTappe] = useState<TappaPercorso[] | null>(null)
  const [erroreCaricamento, setErroreCaricamento] = useState<string | null>(null)
  const [avviso, setAvviso] = useState<Avviso | null>(null)
  /** Lezione in avvio: intanto tutti i nodi sono disattivati (un avvio alla volta). */
  const [inAvvio, setInAvvio] = useState<string | null>(null)
  const nodoDaFare = useRef<HTMLButtonElement | null>(null)
  const scorrimentoFatto = useRef(false)

  const caricamento = ricaricaArrivata !== ricarica

  useEffect(() => {
    let valida = true
    dispatch(caricaMappa())
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

  // la prima volta che la mappa c'è, porta in vista il nodo da fare (jsdom non ha scrollIntoView)
  useEffect(() => {
    if (tappe && !scorrimentoFatto.current) {
      scorrimentoFatto.current = true
      nodoDaFare.current?.scrollIntoView?.({ block: 'center' })
    }
  }, [tappe])

  function rileggi() {
    setRicarica((n) => n + 1)
  }

  async function avvia(lezione: LezionePercorso) {
    if (inAvvio) {
      return
    }
    setInAvvio(lezione.id)
    setAvviso(null)
    try {
      const { tentativoId } = await dispatch(avviaLezione(lezione.id)).unwrap()
      navigate(`/lessons/${lezione.id}`, { state: { tentativoId } })
    } catch (motivo) {
      const codice = isDatiErrore(motivo) ? motivo.codice : 'ERRORE_INTERNO'
      setAvviso(codice === 'NON_TROVATO' ? { tipo: 'inArrivo' } : { tipo: 'errore', codice })
      if (codice === 'NON_TROVATO' || codice === 'LEZIONE_BLOCCATA') {
        rileggi()
      }
      setInAvvio(null)
    }
  }

  // il titolo (nascosto alla vista: c'è già nella barra in alto) esiste in ogni stato della pagina
  const titolo = <h1 className="visually-hidden">Percorso</h1>

  if (tappe === null) {
    if (erroreCaricamento && !caricamento) {
      return (
        <section>
          {titolo}
          <MessaggioErrore codice={erroreCaricamento} />
          <Pulsante onClick={rileggi}>Riprova</Pulsante>
        </section>
      )
    }
    return (
      <section>
        {titolo}
        <p role="status">Caricamento del percorso…</p>
      </section>
    )
  }

  /** Il primo nodo da fare, da portare in vista all'apertura. */
  const idDaFare = tappe.flatMap((tappa) => tappa.lezioni).find((lezione) => lezione.stato === 'SBLOCCATA')?.id

  return (
    <section>
      {titolo}

      {avviso?.tipo === 'inArrivo' && (
        <Scheda className={stili.avviso}>
          <p role="status" className={stili.testoAvviso}>
            Lezione in arrivo, torna a trovarci presto
          </p>
        </Scheda>
      )}
      {avviso?.tipo === 'errore' && <MessaggioErrore codice={avviso.codice} />}
      {erroreCaricamento && <MessaggioErrore codice={erroreCaricamento} />}

      {tappe.length === 0 && <p>Il percorso è in preparazione: torna a trovarci presto.</p>}

      {tappe.map((tappa) => {
        const completate = tappa.lezioni.filter((lezione) => lezione.stato === 'COMPLETATA').length
        return (
          <section key={tappa.id} className={stili.tappa} aria-labelledby={`tappa-${tappa.id}`}>
            <Scheda className={stili.schedaTappa}>
              <div className={stili.rigaTappa}>
                <span className={stili.etichettaTappa}>Tappa {tappa.ordine}</span>
                <span className={stili.completate}>
                  {completate}/{tappa.lezioni.length} completate
                </span>
              </div>
              <h2 id={`tappa-${tappa.id}`} className={stili.titoloTappa}>
                {tappa.titolo}
              </h2>
              <p className={stili.descrizioneTappa}>{tappa.descrizione}</p>
            </Scheda>

            <ol className={stili.nodi}>
              {tappa.lezioni.map((lezione, indice) => (
                <li key={lezione.id} className={`${stili.voce} ${ZIG_ZAG[indice % ZIG_ZAG.length]}`}>
                  <button
                    type="button"
                    ref={lezione.id === idDaFare ? nodoDaFare : undefined}
                    className={`${stili.nodo} ${stili[lezione.stato]}`}
                    disabled={lezione.stato === 'BLOCCATA' || inAvvio !== null}
                    aria-label={`Lezione ${lezione.ordine}, ${lezione.titolo}: ${TESTO_STATO[lezione.stato]}`}
                    onClick={() => avvia(lezione)}
                  >
                    <Icona
                      nome={lezione.stato === 'COMPLETATA' ? 'spunta' : lezione.stato === 'BLOCCATA' ? 'lucchetto' : 'manubrio'}
                      dimensione={32}
                    />
                  </button>
                  {lezione.stato === 'SBLOCCATA' && (
                    <span className={stili.inizia} aria-hidden="true">
                      {inAvvio === lezione.id ? 'Avvio…' : 'Inizia'}
                    </span>
                  )}
                  <span className={stili.titoloLezione} aria-hidden="true">
                    <strong>Lezione {lezione.ordine}</strong> · {lezione.titolo}
                  </span>
                </li>
              ))}
            </ol>
          </section>
        )
      })}
    </section>
  )
}

export default PaginaMappa
