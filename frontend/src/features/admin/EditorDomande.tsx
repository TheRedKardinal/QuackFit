import { useEffect, useState } from 'react'
import { isDatiErrore } from '../../api/ApiError'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import { useInvioForm } from '../../common/useInvioForm'
import { useAppDispatch } from '../../store/hooks'
import { cancellaDomanda, caricaLezione, creaDomanda, modificaDomanda } from './adminThunk'
import ConfermaCancellazione from './ConfermaCancellazione'
import FormModificaDomanda from './FormModificaDomanda'
import FormNuovaDomanda from './FormNuovaDomanda'
import SchedaDomanda from './SchedaDomanda'
import type { DomandaAdmin, LezioneDettaglioAdmin } from './tipi'
import stili from './admin.module.css'

const LEZIONE_PERDUTA = 'Questa lezione non esiste più: scegline un\'altra.'

interface Props {
  /** La lezione scelta (id), o null se non ce n'è. */
  idLezione: string | null
  /** La lezione scelta non c'è più nell'elenco (cancellata): l'editor si svuota con un messaggio. */
  perduta: boolean
  /** "← Tappe": su mobile torna all'elenco (azzera la scelta). */
  onTorna: () => void
  /** Numero di domande dopo ogni caricamento, per aggiornare "Domande (N)" nell'elenco senza rileggerlo. */
  onConteggio: (idLezione: string, numero: number) => void
}

/**
 * Editor delle domande di una lezione (admin.md, Domande): elenco con le soluzioni, nuova domanda, modifica dei soli
 * testi, cancellazione con conferma. Dopo ogni operazione si rileggono solo le domande di questa lezione (la scelta non
 * cambia). Un 404 sul caricamento (lezione cancellata nel frattempo) svuota l'editor con un messaggio.
 */
function EditorDomande({ idLezione, perduta, onTorna, onConteggio }: Props) {
  const dispatch = useAppDispatch()
  const [ricarica, setRicarica] = useState(0)
  const [risposta, setRisposta] = useState<{ chiave: string; lezione?: LezioneDettaglioAdmin; codice?: string } | null>(
    null,
  )
  const [nuova, setNuova] = useState(false)
  const [idInModifica, setIdInModifica] = useState<string | null>(null)
  const [daCancellare, setDaCancellare] = useState<DomandaAdmin | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  const cancellazione = useInvioForm()

  const chiave = idLezione ? `${idLezione}|${ricarica}` : null

  useEffect(() => {
    if (!idLezione || perduta) {
      return
    }
    const questa = `${idLezione}|${ricarica}`
    let valida = true
    dispatch(caricaLezione(idLezione))
      .unwrap()
      .then((lezione) => {
        if (valida) {
          setRisposta({ chiave: questa, lezione })
          onConteggio(idLezione, lezione.domande.length)
        }
      })
      .catch((dati: { codice: string }) => {
        if (valida) {
          setRisposta({ chiave: questa, codice: dati.codice })
        }
      })
    return () => {
      valida = false
    }
  }, [dispatch, idLezione, ricarica, perduta, onConteggio])

  if (!idLezione) {
    return (
      <section aria-label="Editor delle domande" className={stili.editor}>
        <Scheda className={stili.editorVuoto}>
          <p>Scegli una lezione con "Domande" per gestirne le domande.</p>
        </Scheda>
      </section>
    )
  }

  // La lezione mostrata: quella della risposta corrente, o la stessa lezione mentre si ricarica
  const lezione = risposta?.lezione?.id === idLezione ? risposta.lezione : null
  const caricamento = risposta?.chiave !== chiave
  const nonTrovata = perduta || (risposta?.chiave === chiave && risposta.codice === 'NON_TROVATO')
  const erroreCaricamento = risposta?.chiave === chiave && risposta.codice !== 'NON_TROVATO' ? risposta.codice : null

  function rileggi() {
    setRicarica((n) => n + 1)
  }

  /** Salvataggio di un form: se riesce chiude il form e rilegge; un 404 chiude il form, avvisa e rilegge. */
  async function salva(operazione: () => Promise<unknown>, chiudi: () => void) {
    try {
      await operazione()
      setErrore(null)
      chiudi()
      rileggi()
    } catch (motivo) {
      if (isDatiErrore(motivo) && motivo.codice === 'NON_TROVATO') {
        chiudi()
        setErrore(motivo.codice)
        rileggi()
        return
      }
      throw motivo
    }
  }

  async function confermaCancellazione() {
    if (!daCancellare) {
      return
    }
    const id = daCancellare.id
    const risultato = await cancellazione.invia(() => dispatch(cancellaDomanda(id)).unwrap())
    setDaCancellare(null)
    setErrore(risultato ? risultato.codice : null)
    rileggi()
  }

  return (
    <section aria-label="Editor delle domande" className={stili.editor}>
      <Pulsante variante="neutro" className="d-lg-none mb-3" onClick={onTorna}>
        ← Tappe
      </Pulsante>

      {nonTrovata ? (
        <Scheda className={stili.editorVuoto}>
          <p role="status">{LEZIONE_PERDUTA}</p>
        </Scheda>
      ) : (
        <>
          <div className={stili.intestazionePagina}>
            <div>
              <p className={stili.etichettaSezione}>Editor domande</p>
              <h2 className={stili.titoloEditor}>{lezione?.titolo ?? 'Caricamento…'}</h2>
            </div>
            <Pulsante
              onClick={() => {
                setErrore(null)
                setIdInModifica(null)
                setNuova(true)
              }}
              disabled={nuova || !lezione}
            >
              + Nuova domanda
            </Pulsante>
          </div>
          <p className={stili.nota}>
            Le domande non si riordinano: chi studia le vede mescolate. Di una domanda si modificano solo i testi; per
            cambiare tipo, risposta corretta o numero di opzioni si elimina e se ne crea una nuova.
          </p>

          <MessaggioErrore codice={errore ?? erroreCaricamento ?? undefined} />

          {nuova && lezione && (
            <Scheda>
              <FormNuovaDomanda
                onSalva={(corpo) =>
                  salva(() => dispatch(creaDomanda({ lezioneId: lezione.id, dati: corpo })).unwrap(), () => setNuova(false))
                }
                onAnnulla={() => setNuova(false)}
              />
            </Scheda>
          )}

          {caricamento && !lezione && <p>Caricamento…</p>}
          {lezione && lezione.domande.length === 0 && !nuova && (
            <p>Questa lezione non ha ancora domande: finché non ne ha, non si può avviare.</p>
          )}

          {lezione?.domande.map((domanda) => (
            <Scheda key={domanda.id}>
              {idInModifica === domanda.id ? (
                <FormModificaDomanda
                  domanda={domanda}
                  onSalva={(corpo) =>
                    salva(() => dispatch(modificaDomanda({ id: domanda.id, dati: corpo })).unwrap(), () =>
                      setIdInModifica(null),
                    )
                  }
                  onAnnulla={() => setIdInModifica(null)}
                />
              ) : (
                <SchedaDomanda
                  domanda={domanda}
                  onModifica={() => {
                    setErrore(null)
                    setNuova(false)
                    setIdInModifica(domanda.id)
                  }}
                  onElimina={() => setDaCancellare(domanda)}
                />
              )}
            </Scheda>
          ))}
        </>
      )}

      <ConfermaCancellazione
        domanda={daCancellare ? `Eliminare la domanda «${daCancellare.testo}»?` : null}
        inviando={cancellazione.inviando}
        onConferma={confermaCancellazione}
        onAnnulla={() => setDaCancellare(null)}
      />
    </section>
  )
}

export default EditorDomande
