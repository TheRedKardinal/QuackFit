import { Link } from 'react-router-dom'
import Scheda from '../../common/Scheda'
import stili from './privacy.module.css'

/**
 * Privacy e Termini (task 51c): pagina pubblica su /privacy, raggiungibile con o senza accesso (nessuna guardia).
 * Testo statico deciso dall'utente: titolare, dati raccolti, servizi usati, cancellazione, termini e contatto.
 */
function PaginaPrivacy() {
  return (
    <main className={stili.pagina}>
      <header className={stili.barra}>
        <span className={stili.marchio}>QuackFit</span>
        <span className={stili.sezione}>Privacy e Termini</span>
      </header>

      <h1 className={stili.titolo}>Privacy e Termini</h1>

      <Scheda>
        <h2 className={stili.titoloSezione}>Chi gestisce i dati</h2>
        <p>
          QuackFit è un <strong>progetto didattico</strong> (Capstone del corso Epicode). Il titolare dei dati è
          <strong> Claudio Giasi</strong>.
        </p>

        <h2 className={stili.titoloSezione}>Quali dati raccogliamo</h2>
        <ul>
          <li>nome, cognome ed email, per l'account e per i codici di verifica;</li>
          <li>la password, salvata solo come hash: un'impronta da cui non si può risalire alla password;</li>
          <li>la foto del profilo, se la carichi;</li>
          <li>i progressi nelle lezioni e la streak;</li>
          <li>
            l'indirizzo IP, usato solo per limitare i tentativi di accesso: resta nella memoria del server per pochi minuti,
            senza essere salvato nel database né scritto nei log dell'applicazione;
          </li>
          <li>
            nel tuo browser (localStorage) il sito salva il token di accesso, per tenerti connesso; si cancella quando
            esci dall'account.
          </li>
        </ul>

        <h2 className={stili.titoloSezione}>Servizi usati</h2>
        <ul>
          <li>
            <strong>Render</strong>: ospita il sito e il server;
          </li>
          <li>
            <strong>Neon</strong>: ospita il database;
          </li>
          <li>
            <strong>Brevo</strong>: invia le email con i codici di verifica;
          </li>
          <li>
            <strong>Cloudinary</strong>: conserva le foto del profilo.
          </li>
        </ul>

        <h2 className={stili.titoloSezione}>Cancellazione dell'account</h2>
        <p>
          Puoi cancellare l'account in qualsiasi momento da Profilo → Modifica profilo. La cancellazione rende anonimo
          l'account: nome, email e foto vengono eliminati; restano solo i progressi in forma anonima.
        </p>

        <h2 className={stili.titoloSezione}>Termini</h2>
        <p>
          QuackFit è offerto così com'è, a scopo didattico, <strong>senza alcuna garanzia di servizio</strong>: può
          essere interrotto o modificato in qualsiasi momento, e i dati possono essere cancellati in qualsiasi momento.
        </p>

        <h2 className={stili.titoloSezione}>Contatti</h2>
        <p className={stili.ultimo}>
          Per qualsiasi richiesta puoi contattare il titolare tramite il suo profilo GitHub:{' '}
          <a className={stili.link} href="https://github.com/TheRedKardinal" target="_blank" rel="noopener noreferrer">
            github.com/TheRedKardinal
          </a>
          .
        </p>
      </Scheda>

      <p className={stili.ritorno}>
        <Link to="/" className={stili.link}>
          Torna a QuackFit
        </Link>
      </p>
    </main>
  )
}

export default PaginaPrivacy
