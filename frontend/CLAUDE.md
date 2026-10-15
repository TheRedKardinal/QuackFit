# QuackFit – Frontend

Regole specifiche del frontend. Valgono anche tutte le regole comuni di `../CLAUDE.md`.

## Documentazione
Il frontend non ha spec proprie: **il contratto con il backend è la documentazione del backend**.
- `../backend/docs/progettazione.md` → entità, enum, elenco degli endpoint
- `../backend/docs/specs/*.md` → regole, casi limite, status code e codici di errore di ogni endpoint

Prima di implementare una schermata, leggi la spec degli endpoint che usa. Se il backend non fa ciò che serve,
**non aggirare il problema nel frontend**: fermati e segnalalo (la modifica va decisa e fatta nelle spec).
Tutto ciò che non è nella progettazione è fuori perimetro (versione "vanilla").

## Stack
| Cosa | Scelta |
| --- | --- |
| Build | Vite |
| Framework | React |
| Linguaggio | TypeScript (`.tsx`), `strict` attivo, niente `any` |
| Navigazione | React Router |
| Stato globale | Redux Toolkit (unico store) |
| Chiamate HTTP | `fetch` con `async/await`, dentro `createAsyncThunk` |
| UI | Bootstrap 5 (griglia, breakpoint, componenti base) + CSS puro / CSS Modules |
| Font | Nunito Sans, con il pacchetto `@fontsource/nunito-sans` |
| Test | Vitest + React Testing Library + `user-event` |
| Controllo codice | ESLint (quello del template Vite) |

- **Vietati**: Tailwind CSS, Sass/SCSS, axios, librerie di animazione, librerie di traduzione.
- Bootstrap si usa con il solo pacchetto `bootstrap` (CSS). `react-bootstrap` **non è previsto**: chiedere prima.
- **Non aggiungere dipendenze** al `package.json` senza chiedere.

## Struttura (per funzionalità)
```
src
├── api          client HTTP comune (fetch, token, errori) e tipi delle risposte
├── store        configurazione di Redux (store, hook tipizzati)
├── features
│   ├── auth       registrazione, login, verifica email, password dimenticata
│   ├── utente     profilo, modifica dati, password, avatar, cambio email, cancellazione
│   ├── percorso   mappa delle tappe e delle lezioni
│   ├── lezione    svolgimento (quiz), vittoria/sconfitta, resoconto
│   └── admin      gestione utenti e contenuti
├── common       componenti riusabili (pulsanti, drawer, mascotte), errori, utilità
└── styles       variabili CSS (design token) e stili globali
```
- Ogni feature contiene i suoi componenti, il suo slice Redux, i suoi thunk e i suoi tipi.
- Una feature può usare `common` e `api`, ma non gli interni di un'altra feature.

## Convenzioni di codice
- Solo componenti funzionali con hook. Un componente per file, nome del file = nome del componente.
- Nomi del **dominio** in italiano come nel backend (`Tappa`, `Lezione`, `Tentativo`, `lezioniCompletate`);
  i nomi dei campi dei tipi TypeScript sono **identici ai campi JSON** del backend.
- Nomi tecnici di React/Redux nello standard (`useX`, `XProvider`, `xSlice`).
- I tipi delle risposte e delle richieste stanno in `api` o nella feature, uno per ogni DTO.
- Nessuna logica di business duplicata dal backend: il frontend **non calcola** stati di sblocco, esiti o streak
  ufficiali, li legge dalle risposte.
- Testi dell'interfaccia in **italiano**, scritti direttamente nei componenti.
- Variabili d'ambiente: `VITE_API_URL` in `.env.local` (escluso da Git); nel repository c'è `.env.example`.

## Chiamate al backend
- Un solo modulo comune in `api` che:
  - antepone `VITE_API_URL`
  - aggiunge l'header `Authorization: Bearer <token>` quando il token esiste
  - legge gli errori nel formato del backend `{ status, codice, errore, messaggio, campi, timestamp }`
    e li trasforma in un `ApiError` con `codice`, `messaggio` e `campi`
- Le schermate non usano `fetch` direttamente: passano dai thunk della loro feature.
- Per l'upload dell'avatar si invia `multipart/form-data` (campo `file`), senza impostare a mano `Content-Type`.

## Errori
- La logica si basa sempre sul **`codice`** dell'errore, mai sul testo del `messaggio` del backend.
- Un file unico in `common` traduce ogni `codice` in un messaggio italiano per l'utente.
- Per `VALIDAZIONE` si mostrano gli errori dei singoli `campi` accanto ai relativi campi del form.
- Comportamenti obbligatori:
  | Situazione | Cosa fa il frontend |
  | --- | --- |
  | 401 (`NON_AUTENTICATO`) su una richiesta con token | cancella il token e lo stato, porta al login |
  | 403 `EMAIL_NON_VERIFICATA` | porta alla schermata del codice di verifica |
  | 403 `LEZIONE_BLOCCATA` | torna alla mappa con un messaggio |
  | 403 `ACCESSO_NEGATO` | messaggio "non hai i permessi" |
  | 409 `TENTATIVO_CHIUSO` | torna alla mappa con un messaggio |
  | 429 `ATTENDI_REINVIO` / `TROPPE_RICHIESTE` | messaggio e pulsante disattivato per un po' |
  | 500 e errori di rete | messaggio generico, mai dettagli tecnici |
- Il login fallito mostra **sempre il messaggio unico del backend** (non distingue email o password errate).

## Sicurezza
- Il token JWT si salva in **`localStorage`** e viaggia nell'header `Authorization`. Al logout si cancellano il token
  e tutto lo stato Redux.
- Contro l'XSS: **mai `dangerouslySetInnerHTML`**, mai HTML costruito con testi di utenti o di contenuti;
  i testi passano sempre da React, che fa l'escape. Nessun `eval` né `innerHTML`.
- Nessun segreto nel frontend: tutto ciò che sta in `VITE_*` finisce nel bundle ed è pubblico.
- Dopo **cambio password, cambio email, cancellazione account** il backend invalida il token:
  il frontend fa logout e porta al login.
- Le schermate admin si nascondono ai non-admin, ma è solo comodità: i permessi veri li decide il backend.
- Percorsi protetti (guardie del router): utente autenticato → email verificata → ruolo admin per `/admin`.

## Schermate e comportamento

### Mappa — `GET /api/path/stages`
- Tappe e nodi in verticale. Stati calcolati dal backend: `COMPLETATA` (verde, spunta), `SBLOCCATA` (gialla, attiva),
  `BLOCCATA` (grigia, lucchetto). Non si mostrano numero di domande né contenuti prima dell'avvio.

### Lezione — `POST /api/attempts`, `/answers`, `/complete`
- Barra in alto: pulsante di chiusura (esce senza chiamare il backend), barra di avanzamento e indicatore di errori
  **"Errori x/3"** (tre pallini). **Niente cuori né vite**. `MAX_ERRORI = 3` è una costante del frontend.
- Domande nell'ordine in cui arrivano (già mescolate dal backend). Tipi: `VERO_FALSO` (due pulsanti grandi),
  `SCELTA_MULTIPLA` (selezione singola), `ABBINAMENTO` (tessere sinistra ↔ destra, si inviano **tutte le coppie insieme**).
- Si invia **una risposta alla volta**; il pulsante si disattiva durante la richiesta (niente invii doppi).
  Soluzione e spiegazione compaiono **solo dopo** la risposta, in un drawer verde (corretta) o rosso (sbagliata).
- Le domande sbagliate vengono **riproposte a fine sequenza** finché risolte (`risolta` lato backend) o fino al terzo errore.
  Il backend decide l'esito: il frontend legge `esito` ed `errori` dalle risposte.
- Esito `FALLITO` (terzo errore): schermata di sconfitta con possibilità di riprovare (nuovo `POST /api/attempts`).
- Quando tutte le domande sono risolte: `POST /complete`, poi schermata di vittoria.
- Lo stato del tentativo vive in Redux: se la pagina viene ricaricata durante la lezione, il tentativo si perde e
  l'utente la riavvia (il backend chiude il precedente come `FALLITO`).

### Vittoria
- Celebrazione con Coach Quack, streak (valore e record) dalla risposta di `complete`.
- Tracker della settimana L-M-M-G-V-S-D **dedotto dal valore della streak** (con streak N e ultima lezione oggi si
  colorano oggi e i N-1 giorni precedenti). "+1 OGGI" solo se il valore è aumentato rispetto a quello precedente.
- Riquadro: esito, errori commessi (x/3), **tempo impiegato** (timer nel frontend, avviato all'avvio del tentativo).
- Pulsanti: "Continua" (mappa) e "Rivedi risposte" (`GET /api/attempts/{id}`).

### Profilo — `GET /api/me`
- Avatar, nome, cognome, email, etichetta del ruolo; solo tre metriche: streak attuale, record, lezioni completate.
- `emailVerificata` serve solo a decidere dove portare l'utente (schermata del codice); non si mostra nel profilo.
- Modifica nome e cognome, cambio password, cambio email (con codice), cambio avatar, cancellazione account.
- **Avatar**: campo file con `accept="image/*"` **senza** `capture` (galleria o fotocamera). Prima dell'invio si
  ridimensiona su un canvas a circa 512×512 e si comprime in jpeg/webp per restare sotto i 2 MB.

### Admin (conforme a `../backend/docs/specs/admin.md`)
- **Desktop**: sidebar fissa; utenti in tabella; contenuti su due colonne (tappe e lezioni a sinistra, editor domande a destra).
- **Mobile**: barra di navigazione in basso (Utenti, Contenuti), card al posto delle tabelle, accordion per le tappe.
- Utenti: ricerca `q`, paginazione (`size` massimo 50). Nascondere le azioni di ruolo non consentite (non su sé stessi,
  non su un SUPERADMIN, solo il SUPERADMIN retrocede un ADMIN), ma gestire comunque il 403.
- Contenuti: riordino di tappe e lezioni con pulsanti **Su / Giù** (`PATCH` con `ordine`); le domande **non si riordinano**.
  Nell'editor domande le soluzioni sono visibili (solo admin). Nella modifica di una domanda si cambiano solo i testi;
  per cambiare tipo o struttura si cancella e si ricrea. Cancellare chiede sempre conferma.

### Fuori perimetro (non realizzare)
XP e punti, badge e traguardi, livelli, classifiche, vite o cuori, username, pixel-art evolutiva della mascotte.

## Design
### Direzione visiva
- Mobile first rigoroso: pensato per ~390 px di larghezza, con layout adattivo per desktop (soprattutto l'admin).
- Coach Quack: papero atletico con fascia sportiva rossa, **illustrazione flat in SVG con animazioni CSS**
  (nessuna immagine esterna né libreria).
- Pulsanti e card in stile tattile, con bordo inferiore in rilievo (effetto "push").
- Font: Nunito Sans (`@fontsource/nunito-sans`, importato in `main.tsx`, solo i pesi usati).

### Riferimento grafico (Stitch)
- Le schermate di Stitch sono in `frontend/design/stitch/` (con `README.md` che le associa alle schermate dell'app).
- Sono **solo un riferimento visivo**: il codice HTML esportato non si copia. Si riscrive come componenti React in
  TypeScript, con le nostre variabili CSS, la struttura per funzionalità e senza dipendenze nuove.
- Se una schermata prevista non ha un riferimento, si segue lo stile delle altre e si segnala all'utente.

### Design token (variabili CSS in `src/styles`)
| Uso | Valore |
| --- | --- |
| Primario (brand, pulsanti, nodo attivo) | `#ffd200` |
| Successo (risposta giusta, "Continua", superato) | `#58cc02` |
| Streak | `#ff9600` |
| Sfondo | `#f9f9f7` |
| Superficie | `#ffffff` |
| Bordi | `#e5e7eb` |

- Mai valori di colore scritti a mano nei componenti: si usano sempre le variabili.
- Contrasto e accessibilità di base: elementi cliccabili sono veri `<button>` o `<a>`, campi con `<label>`, stati non
  affidati al solo colore (icone e testo oltre al verde/rosso).

## Test
- Vitest + React Testing Library + `user-event`, solo dove c'è logica o interazione:
  - gestione della lezione (coda di domande, riproposta, errori, esito)
  - traduzione dei codici di errore e comportamento su 401/403
  - tracker della streak
  - interazioni: doppio click su "Verifica" invia una sola risposta, click su un'opzione dopo l'esito non fa nulla,
    pulsanti disattivati durante le richieste
- Niente test sulle singole schermate statiche; lo scroll e i flussi end-to-end non sono coperti.
- I test devono passare prima di proporre un commit.

## Verifica nel browser
Per ogni schermata, prima di dichiarare finita la task, si verifica che funzioni davvero con **Claude in Chrome**:
- avviare il frontend (`npm run dev`) e il backend in locale (in locale i codici email si leggono nel log del backend)
- aprire la pagina su `localhost`, percorrere il flusso della task, controllare errori in console e richieste di rete
- provare anche a larghezza mobile (~390 px) e, per l'admin, a larghezza desktop
- usare solo dati di prova e **mai** credenziali reali dell'utente; le azioni irreversibili (cancellazione account) si provano solo su un utente creato apposta

## Comandi
Il progetto gira su Windows (npm):
- sviluppo: `npm run dev`
- build: `npm run build`
- test: `npm test`
- controllo del codice: `npm run lint` (prima di ogni commit)
