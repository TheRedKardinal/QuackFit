import { useEffect, useState, type ChangeEvent } from 'react'
import FotoProfilo from '../../common/FotoProfilo'
import { ImmagineNonValidaError, preparaAvatar } from '../../common/immagine'
import { messaggioErrore } from '../../common/messaggiErrore'
import MessaggioErrore from '../../common/MessaggioErrore'
import Pulsante from '../../common/Pulsante'
import Scheda from '../../common/Scheda'
import TestoPulsante from '../../common/TestoPulsante'
import { useInvioForm } from '../../common/useInvioForm'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import stili from './profilo.module.css'
import { caricaFoto } from './utenteThunk'

interface Anteprima {
  foto: Blob
  url: string
}

/**
 * Foto profilo (me.md, Foto profilo): galleria o fotocamera (accept="image/*" senza capture), ritaglio al centro e
 * riduzione a 512×512 nel browser, anteprima, poi "Salva foto". Nessuna chiamata finché l'utente non conferma.
 */
function SezioneFoto() {
  const dispatch = useAppDispatch()
  const utente = useAppSelector((stato) => stato.sessione.utente)
  const { invia, inviando, secondiAttesa, bloccato } = useInvioForm()
  const [anteprima, setAnteprima] = useState<Anteprima | null>(null)
  const [preparando, setPreparando] = useState(false)
  const [errore, setErrore] = useState<string | null>(null)
  const [salvata, setSalvata] = useState(false)

  // l'URL dell'anteprima occupa memoria finché non lo si libera
  useEffect(() => {
    return () => {
      if (anteprima) {
        URL.revokeObjectURL(anteprima.url)
      }
    }
  }, [anteprima])

  async function scegli(evento: ChangeEvent<HTMLInputElement>) {
    const file = evento.target.files?.[0]
    // si svuota il campo, così si può scegliere di nuovo la stessa foto
    evento.target.value = ''
    if (!file) {
      return
    }
    setErrore(null)
    setSalvata(false)
    setPreparando(true)
    try {
      const foto = await preparaAvatar(file)
      setAnteprima({ foto, url: URL.createObjectURL(foto) })
    } catch (e) {
      setAnteprima(null)
      setErrore(e instanceof ImmagineNonValidaError ? e.message : messaggioErrore('ERRORE_INTERNO'))
    } finally {
      setPreparando(false)
    }
  }

  async function salva() {
    if (!anteprima || bloccato) {
      return
    }
    setErrore(null)
    const risultato = await invia(() => dispatch(caricaFoto(anteprima.foto)).unwrap())
    if (risultato) {
      setErrore(messaggioErrore(risultato.codice))
    } else {
      setAnteprima(null)
      setSalvata(true)
    }
  }

  if (!utente) {
    return null
  }

  return (
    <Scheda>
      <h2 className={stili.titoloSezione}>Foto profilo</h2>
      <div className={stili.zonaFoto}>
        {anteprima ? (
          <img src={anteprima.url} alt="Anteprima della nuova foto" className={stili.anteprima} />
        ) : (
          <FotoProfilo utente={utente} dimensione={6} />
        )}
      </div>

      {salvata && (
        <p role="status" className={stili.messaggioOk}>
          ✓ Foto aggiornata.
        </p>
      )}
      <MessaggioErrore testo={errore} />

      <div className={stili.pulsanti}>
        {anteprima ? (
          <>
            <Pulsante variante="primario" onClick={salva} disabled={bloccato}>
              <TestoPulsante testo="Salva foto" inviando={inviando} secondiAttesa={secondiAttesa} />
            </Pulsante>
            <Pulsante variante="neutro" onClick={() => setAnteprima(null)} disabled={inviando}>
              Annulla
            </Pulsante>
          </>
        ) : (
          <>
            <input
              id="foto"
              type="file"
              accept="image/*"
              className={`visually-hidden ${stili.inputFoto}`}
              onChange={scegli}
              disabled={preparando}
            />
            <label htmlFor="foto" className="btn-push btn-push-neutro text-center">
              {preparando ? 'Preparo la foto…' : 'Cambia foto'}
            </label>
          </>
        )}
      </div>
    </Scheda>
  )
}

export default SezioneFoto
