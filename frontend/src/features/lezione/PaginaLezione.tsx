import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import Drawer from '../../common/Drawer'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import { useInvioForm } from '../../common/useInvioForm'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import BarraLezione from './BarraLezione'
import { abbinamentoVuoto, completa, coppieDaInviare, toccaDestro, toccaSinistro } from './abbinamento'
import { avanza, domandaCorrente, finita, risolte } from './codaLezione'
import DomandaAbbinamento from './DomandaAbbinamento'
import DomandaOpzioni from './DomandaOpzioni'
import { avanti, lezioneIniziata } from './lezioneSlice'
import { caricaContenuto, chiudiTentativo, inviaRisposta, riavviaLezione } from './lezioneThunk'
import SchermataSconfitta from './SchermataSconfitta'
import SchermataVittoria from './SchermataVittoria'
import { durata } from './tempo'
import type { DomandaLezione, RispostaRequest, TipoDomanda } from './tipi'
import stili from './lezione.module.css'

/** Stesso testo del 400 della chiusura (tentativi.md, Chiusura): si mostra senza dipendere dal messaggio del backend. */
const MSG_NON_TUTTE_RISOLTE = 'Rispondi correttamente a tutte le domande prima di concludere la lezione.'

const NOME_TIPO: Record<TipoDomanda, string> = {
  VERO_FALSO: 'Vero o falso',
  SCELTA_MULTIPLA: 'Scelta multipla',
  ABBINAMENTO: 'Abbinamento',
}

/** Testo di un elemento dell'abbinamento (le soluzioni arrivano solo con gli id). */
function testoElemento(domanda: DomandaLezione, id: string): string {
  return [...domanda.sinistra, ...domanda.destra].find((voce) => voce.id === id)?.testo ?? ''
}

/**
 * Svolgimento di una lezione (frontend/CLAUDE.md, Lezione). Il tentativo arriva dalla mappa nello state del router
 * (`tentativoId`): senza (es. pagina ricaricata) si torna alla mappa e la lezione si riavvia da lì.
 * - una domanda alla volta, nell'ordine in cui arrivano; si sceglie (un'opzione, oppure tutte le coppie di un
 *   abbinamento) e si preme "Verifica" (una richiesta alla volta)
 * - esito nel drawer verde o rosso con soluzione e spiegazione; "Continua" passa alla prossima, le sbagliate tornano
 *   a fine giro (codaLezione); quando non ne restano si chiude il tentativo (complete) e si mostra la vittoria
 * - esito FALLITO (terzo errore): dopo il drawer, la sconfitta con "Riprova" (nuovo tentativo)
 * - 409 TENTATIVO_CHIUSO o contenuto non più disponibile: si torna alla mappa con il messaggio
 * - 400 VALIDAZIONE sulla domanda (es. "Domanda già risolta" di un doppio tap): si ignora in silenzio
 */
function PaginaLezione() {
  const { id: lezioneId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const lezione = useAppSelector((stato) => stato.lezione)
  const streakAttuale = useAppSelector((stato) => stato.sessione.utente?.streak.valore ?? 0)
  const tentativoId = (location.state as { tentativoId?: string } | null)?.tentativoId ?? null
  const [selezionata, setSelezionata] = useState<string | null>(null)
  const [abbinamento, setAbbinamento] = useState(abbinamentoVuoto)
  const [errore, setErrore] = useState<string | null>(null)
  const [erroreChiusura, setErroreChiusura] = useState<string | null>(null)
  const invio = useInvioForm()
  const chiusura = useInvioForm()
  const riavvio = useInvioForm()

  // il tentativo del router diventa quello dello store (Date.now() qui: i reducer restano puri); la streak di /api/me
  // di adesso serve alla vittoria per decidere se mostrare "+1 OGGI"
  useEffect(() => {
    if (tentativoId && lezioneId && lezione.tentativoId !== tentativoId) {
      dispatch(lezioneIniziata({ tentativoId, lezioneId, inizio: Date.now(), streakPrima: streakAttuale }))
    }
  }, [dispatch, tentativoId, lezioneId, lezione.tentativoId, streakAttuale])

  // contenuto del tentativo appena iniziato (anche dopo "Riprova": il backend rimescola le domande)
  useEffect(() => {
    if (!lezione.tentativoId || lezione.caricata || !lezioneId) {
      return
    }
    let valida = true
    dispatch(caricaContenuto(lezioneId))
      .unwrap()
      .catch((dati: { codice: string }) => {
        if (valida) {
          navigate('/', { replace: true, state: { codiceErrore: dati.codice } })
        }
      })
    return () => {
      valida = false
    }
  }, [dispatch, navigate, lezione.tentativoId, lezione.caricata, lezioneId])

  if (!tentativoId || !lezioneId) {
    return <Navigate to="/" replace />
  }

  function tornaAllaMappa(codiceErrore?: string) {
    navigate('/', codiceErrore ? { state: { codiceErrore } } : undefined)
  }

  async function chiudi() {
    const dati = await chiusura.invia(() => dispatch(chiudiTentativo()).unwrap())
    if (dati?.codice === 'TENTATIVO_CHIUSO') {
      tornaAllaMappa(dati.codice)
    } else if (dati) {
      setErroreChiusura(dati.codice === 'VALIDAZIONE' ? MSG_NON_TUTTE_RISOLTE : null)
      if (dati.codice !== 'VALIDAZIONE') {
        setErrore(dati.codice)
      }
    }
  }

  /** La risposta da inviare per la domanda, oppure null se non è ancora pronta (nessuna opzione o coppie incomplete). */
  function rispostaPronta(domanda: DomandaLezione): RispostaRequest | null {
    if (domanda.tipo === 'ABBINAMENTO') {
      const sinistri = domanda.sinistra.map((voce) => voce.id)
      return completa(abbinamento, sinistri)
        ? { domandaId: domanda.id, coppie: coppieDaInviare(abbinamento, sinistri) }
        : null
    }
    return selezionata ? { domandaId: domanda.id, opzioneId: selezionata } : null
  }

  async function verifica(domanda: DomandaLezione) {
    const risposta = rispostaPronta(domanda)
    if (!risposta || lezione.ultimaRisposta) {
      return
    }
    setErrore(null)
    const dati = await invio.invia(() => dispatch(inviaRisposta(risposta)).unwrap())
    if (!dati) {
      return
    }
    if (dati.codice === 'TENTATIVO_CHIUSO' || dati.codice === 'NON_TROVATO') {
      tornaAllaMappa(dati.codice)
    } else if (!(dati.codice === 'VALIDAZIONE' && dati.campi.domandaId)) {
      setErrore(dati.codice)
    }
  }

  /** "Continua" nel drawer: prossima domanda; finite le domande (e non fallito) si chiude il tentativo. */
  function continua() {
    const ultima = lezione.ultimaRisposta
    if (!ultima) {
      return
    }
    const prossima = avanza(lezione.coda, ultima.esito.corretta)
    dispatch(avanti())
    setSelezionata(null)
    setAbbinamento(abbinamentoVuoto)
    if (lezione.esito === 'IN_CORSO' && finita(prossima)) {
      void chiudi()
    }
  }

  async function riprova() {
    const dati = await riavvio.invia(async () => {
      const nuovo = await dispatch(riavviaLezione(lezioneId as string)).unwrap()
      // il nuovo tentativo va anche nello state del router, così l'effetto iniziale non torna al precedente
      navigate(location.pathname, { replace: true, state: { tentativoId: nuovo.tentativoId } })
    })
    setErrore(dati?.codice ?? null)
    setSelezionata(null)
    setAbbinamento(abbinamentoVuoto)
    setErroreChiusura(null)
  }

  if (lezione.tentativoId !== tentativoId || !lezione.caricata) {
    return (
      <main className={stili.pagina}>
        <p role="status">Caricamento della lezione…</p>
      </main>
    )
  }

  if (lezione.chiusura) {
    return (
      <main className={stili.pagina}>
        <SchermataVittoria
          streak={lezione.chiusura.streak}
          aumentata={lezione.chiusura.streak.valore > lezione.streakPrima}
          errori={lezione.errori}
          tempo={durata(lezione.inizio, lezione.fine)}
          oggi={new Date(lezione.fine ?? 0)}
          onContinua={() => tornaAllaMappa()}
          onRivediRisposte={() => navigate(`/attempts/${lezione.tentativoId}`)}
        />
      </main>
    )
  }

  if (lezione.esito === 'FALLITO' && !lezione.ultimaRisposta) {
    return (
      <main className={stili.pagina}>
        <SchermataSconfitta
          errori={lezione.errori}
          tempo={durata(lezione.inizio, lezione.fine)}
          riavviando={riavvio.inviando}
          errore={errore}
          onRiprova={riprova}
          onMappa={() => tornaAllaMappa()}
        />
      </main>
    )
  }

  const idCorrente = domandaCorrente(lezione.coda)
  const domanda = lezione.domande.find((d) => d.id === idCorrente)
  const ultima = lezione.ultimaRisposta

  return (
    <main className={stili.pagina}>
      <BarraLezione
        risolte={risolte(lezione.coda, lezione.domande.length)}
        totale={lezione.domande.length}
        errori={lezione.errori}
        onChiudi={() => tornaAllaMappa()}
      />

      <h1 className="visually-hidden">{lezione.titolo}</h1>

      {domanda && (
        <section aria-labelledby="testo-domanda">
          <p className={stili.tipo}>{NOME_TIPO[domanda.tipo]}</p>
          <h2 id="testo-domanda" className={stili.testoDomanda}>
            {domanda.testo}
          </h2>
          {domanda.tipo === 'ABBINAMENTO' ? (
            <DomandaAbbinamento
              domanda={domanda}
              stato={abbinamento}
              bloccata={ultima !== null || invio.inviando}
              soluzione={ultima ? ultima.esito.soluzione.coppie : null}
              onToccaSinistro={(id) => setAbbinamento((stato) => toccaSinistro(stato, id))}
              onToccaDestro={(id) => setAbbinamento((stato) => toccaDestro(stato, id))}
            />
          ) : (
            <DomandaOpzioni
              domanda={domanda}
              selezionata={ultima?.opzioneId ?? selezionata}
              bloccata={ultima !== null || invio.inviando}
              soluzione={ultima?.esito.soluzione.opzioneId ?? null}
              onSeleziona={setSelezionata}
            />
          )}
          <MessaggioErrore codice={errore ?? undefined} />
          {!ultima && (
            <Pulsante
              larghezzaPiena
              disabled={!rispostaPronta(domanda) || invio.inviando}
              onClick={() => verifica(domanda)}
            >
              {invio.inviando ? 'Verifica…' : 'Verifica'}
            </Pulsante>
          )}
        </section>
      )}

      {!domanda && (
        <section>
          {chiusura.inviando && <p role="status">Chiusura della lezione…</p>}
          <MessaggioErrore testo={erroreChiusura} />
          <MessaggioErrore codice={erroreChiusura ? undefined : (errore ?? undefined)} />
          {!chiusura.inviando && (
            <Pulsante variante="neutro" larghezzaPiena onClick={() => tornaAllaMappa()}>
              Torna alla mappa
            </Pulsante>
          )}
        </section>
      )}

      <Drawer
        aperto={ultima !== null}
        tono={ultima?.esito.corretta ? 'successo' : 'errore'}
        titolo={ultima?.esito.corretta ? 'Risposta giusta!' : 'Risposta sbagliata'}
      >
        {ultima && !ultima.esito.corretta && domanda && domanda.tipo !== 'ABBINAMENTO' && (
          <p>
            <strong>Risposta giusta:</strong>{' '}
            {domanda.opzioni.find((o) => o.id === ultima.esito.soluzione.opzioneId)?.testo}
          </p>
        )}
        {ultima && domanda && domanda.tipo === 'ABBINAMENTO' && (
          <ul className={stili.esitoCoppie}>
            {ultima.coppie.map((coppia) => {
              const giusto = ultima.esito.soluzione.coppie.find((c) => c.sinistroId === coppia.sinistroId)?.destroId
              const corretta = giusto === coppia.destroId
              return (
                <li key={coppia.sinistroId}>
                  <span aria-hidden="true">{corretta ? '✓' : '✗'}</span> {testoElemento(domanda, coppia.sinistroId)} →{' '}
                  {testoElemento(domanda, coppia.destroId)}
                  <span className="visually-hidden">{corretta ? ', giusta' : ', sbagliata'}</span>
                  {!corretta && giusto && <> (giusto: {testoElemento(domanda, giusto)})</>}
                </li>
              )
            })}
          </ul>
        )}
        <p>{ultima?.esito.spiegazione}</p>
        <Pulsante variante={ultima?.esito.corretta ? 'successo' : 'pericolo'} larghezzaPiena onClick={continua}>
          Continua
        </Pulsante>
      </Drawer>
    </main>
  )
}

export default PaginaLezione
