import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import { useAppDispatch } from '../../store/hooks'
import { MAX_ERRORI } from './codaLezione'
import { caricaResoconto } from './lezioneThunk'
import type { DomandaResoconto, EsitoTentativo, Resoconto, TipoDomanda } from './tipi'
import stili from './lezione.module.css'

const NOME_TIPO: Record<TipoDomanda, string> = {
  VERO_FALSO: 'Vero o falso',
  SCELTA_MULTIPLA: 'Scelta multipla',
  ABBINAMENTO: 'Abbinamento',
}

const NOME_ESITO: Record<EsitoTentativo, string> = {
  SUPERATO: 'Superato',
  FALLITO: 'Non superato',
  IN_CORSO: 'In corso',
}

/** Testo del 404 solo in questa pagina: tentativo inesistente o di un altro utente (non si distinguono). */
const MSG_NON_DISPONIBILE = 'Questo resoconto non è disponibile.'

function domande(n: number): string {
  return n === 1 ? '1 domanda' : `${n} domande`
}

function SchedaDomanda({ domanda, numero }: { domanda: DomandaResoconto; numero: number }) {
  return (
    <Scheda>
      <p className={stili.intestazioneDomanda}>
        <span className={stili.tipo}>
          D{numero} · {NOME_TIPO[domanda.tipo]}
        </span>
        <span className={domanda.corretta ? stili.esitoGiusto : stili.esitoSbagliato}>
          {domanda.corretta ? '✓ Corretta' : '✗ Da rivedere'}
        </span>
      </p>
      <h2 className={stili.testoDomandaResoconto}>{domanda.testo}</h2>

      {domanda.risposta && (
        <p className={`${stili.rigaRisposta} ${domanda.corretta ? stili.giusta : stili.sbagliata}`}>
          <strong>La tua risposta{domanda.corretta ? '' : ' (errata)'}:</strong> {domanda.risposta.testo}
        </p>
      )}
      {!domanda.corretta && domanda.soluzione && (
        <p className={`${stili.rigaRisposta} ${stili.giusta}`}>
          <strong>Risposta corretta:</strong> {domanda.soluzione.testo}
        </p>
      )}

      {domanda.coppie.length > 0 && (
        <ul className={stili.coppieResoconto}>
          {domanda.coppie.map((coppia) => (
            <li
              key={coppia.sinistro.id}
              className={`${stili.rigaRisposta} ${coppia.corretta ? stili.giusta : stili.sbagliata}`}
            >
              <span aria-hidden="true">{coppia.corretta ? '✓' : '✗'}</span> {coppia.sinistro.testo} →{' '}
              {coppia.destro.testo}
              <span className="visually-hidden">{coppia.corretta ? ', giusta' : ', sbagliata'}</span>
              {!coppia.corretta && <> (giusto: {coppia.soluzione.testo})</>}
            </li>
          ))}
        </ul>
      )}

      <p className={stili.spiegazione}>
        <strong>Spiegazione:</strong> {domanda.spiegazione}
      </p>
    </Scheda>
  )
}

/**
 * Resoconto "Rivedi risposte" (tentativi.md, Resoconto): esito, lezione, errori e le domande già risposte nell'ordine
 * dell'admin, con la prima risposta, se era giusta, la soluzione e la spiegazione. 404 (tentativo inesistente o di un
 * altro utente): messaggio dedicato; rete o 500: messaggio d'errore normale con "Riprova".
 * La tappa, XP e il tempo della schermata Stitch non sono nella risposta del backend o sono fuori perimetro.
 */
function PaginaResoconto() {
  const { id } = useParams()
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  /** Aumenta per rileggere ("Riprova"). */
  const [ricarica, setRicarica] = useState(0)
  const [ricaricaArrivata, setRicaricaArrivata] = useState<number | null>(null)
  const [resoconto, setResoconto] = useState<Resoconto | null>(null)
  const [errore, setErrore] = useState<string | null>(null)

  const caricamento = ricaricaArrivata !== ricarica

  useEffect(() => {
    if (!id) {
      return
    }
    let valida = true
    dispatch(caricaResoconto(id))
      .unwrap()
      .then((dati) => {
        if (valida) {
          setResoconto(dati)
          setErrore(null)
          setRicaricaArrivata(ricarica)
        }
      })
      .catch((dati: { codice: string }) => {
        if (valida) {
          setErrore(dati.codice)
          setRicaricaArrivata(ricarica)
        }
      })
    return () => {
      valida = false
    }
  }, [dispatch, id, ricarica])

  const tornaAllaMappa = (
    <Pulsante variante="neutro" larghezzaPiena onClick={() => navigate('/')}>
      Torna alla mappa
    </Pulsante>
  )

  let contenuto
  if (caricamento) {
    contenuto = <p role="status">Caricamento del resoconto…</p>
  } else if (errore === 'NON_TROVATO') {
    contenuto = (
      <>
        <MessaggioErrore testo={MSG_NON_DISPONIBILE} />
        {tornaAllaMappa}
      </>
    )
  } else if (errore || !resoconto) {
    contenuto = (
      <>
        <MessaggioErrore codice={errore ?? 'ERRORE_INTERNO'} />
        <div className={stili.azioniFine}>
          <Pulsante onClick={() => setRicarica((n) => n + 1)}>Riprova</Pulsante>
          {tornaAllaMappa}
        </div>
      </>
    )
  } else {
    contenuto = (
      <>
        <Scheda>
          <p className={stili.intestazioneDomanda}>
            <span className={resoconto.esito === 'SUPERATO' ? stili.esitoGiusto : stili.esitoSbagliato}>
              {resoconto.esito === 'SUPERATO' ? '✓' : '✗'} {NOME_ESITO[resoconto.esito]}
            </span>
          </p>
          <p className={stili.titoloLezioneResoconto}>{resoconto.lezione.titolo}</p>
          <p className="mb-0">
            Errori {resoconto.errori}/{MAX_ERRORI} · {domande(resoconto.domande.length)}
          </p>
        </Scheda>
        {resoconto.domande.length === 0 && <p>Nessuna domanda risposta in questo tentativo.</p>}
        {resoconto.domande.map((domanda, indice) => (
          <SchedaDomanda key={domanda.id} domanda={domanda} numero={indice + 1} />
        ))}
        {tornaAllaMappa}
      </>
    )
  }

  return (
    <section>
      <h1 className="h3 fw-bold">Resoconto</h1>
      {contenuto}
    </section>
  )
}

export default PaginaResoconto
