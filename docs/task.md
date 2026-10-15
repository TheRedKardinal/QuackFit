# QuackFit – Elenco delle task (fonte per Trello)

Ogni task è una card su Trello. Le card sono scritte in modo che **si possa riprendere il lavoro senza la cronologia
della chat**: la card dice cosa leggere (i file del repository sono la memoria del progetto), da quali task dipende
e come capire quando è finita. Questo file è la copia di riserva del contenuto delle card.

## Come si lavora su una card
1. Leggi la card, i `CLAUDE.md` (radice e cartella) e solo i file indicati in **Leggi**.
2. Controlla che le task in **Dipende da** siano in `Fatto` (o già unite in `main`).
3. Spiega cosa farai e quali file toccherai, aspetta l'ok, poi sposta la card in **In corso** e crea il branch.
4. Segui gli step della checklist, spuntandoli man mano.
5. Se manca una decisione o la spec non basta: **fermati e chiedi** (regola d'oro del `CLAUDE.md`).

## Step finali (valgono per ogni card, anche dove non compaiono nella checklist di Trello)
- Test della feature passano (e `npm run lint` pulito per il frontend)
- Spec e `progettazione.md` allineati al codice
- Commit proposti e approvati dall'utente
- PR aperta verso `main` (il merge lo fa l'utente) e card spostata in **In revisione**

Nei card del **frontend** gli step includono anche la **verifica nel browser con Claude in Chrome** sul server locale
(`npm run dev` + backend avviato): la verifica serve a controllare che la schermata funzioni davvero, non solo che compili.

## Etichette
Backend · Frontend · Docs/Deploy

---

# Fase 0 – Setup

### 01 · Docs/Deploy · Repository e documentazione
- Branch: `chore/setup-repo` · Dipende da: nessuna
- Leggi: `CLAUDE.md` (radice)
- Obiettivo: repository con la struttura finale, i file di regole e i documenti al loro posto, senza segreti tracciati.
- Step:
  - verificare che esistano: `CLAUDE.md` (radice, `backend/`, `frontend/`), `.claude/settings.json`,
    `backend/docs/progettazione.md`, `backend/docs/specs/` (auth, me, percorso, tentativi, admin), `docs/task.md`
  - creare `.gitignore` (`env.properties`, `.env*`, `.env.trello`, `target/`, `node_modules/`, `dist/`, file degli IDE)
  - creare un `README.md` minimo (nome, descrizione di una riga, struttura del repository)
  - dopo il primo push controllare su GitHub che **Claude non compaia** tra i collaboratori né come co-autore dei commit
    (se compare: correggere `.claude/settings.json`)

### 02 · Backend · Progetto Spring Boot e configurazione
- Branch: `chore/backend-setup` · Dipende da: 01
- Leggi: `backend/CLAUDE.md` (Stack, Struttura dei package, Configurazione)
- Obiettivo: applicazione Spring Boot che parte, con profili e configurazione da `env.properties`.
- Step:
  - **L'utente crea il database PostgreSQL locale** `quackfit` (e annota utente e password in `env.properties`)
  - creare il progetto in `backend/` (Java 25, Maven, Spring Boot 4.1.1) con le dipendenze dello stack
    (Web, Data JPA, Security, Validation, Mail, Thymeleaf, PostgreSQL, H2, Lombok, jjwt); Maven Wrapper incluso
  - creare i package vuoti di `it.quackfit` come da `backend/CLAUDE.md`
  - `application.properties` che importa `env.properties`; `env.properties.example` con le chiavi vuote
  - profili `default` (PostgreSQL locale), `test` (H2 in memoria), `prod` (variabili d'ambiente); `ddl-auto=update`
  - un test di avvio (`contextLoads`) con profilo `test`
  - verificare `mvnw.cmd spring-boot:run` e `mvnw.cmd test`

### 03 · Backend · Errori comuni e orologio
- Branch: `feature/common-errori` · Dipende da: 02
- Leggi: `backend/CLAUDE.md` (Errori, Tempo)
- Obiettivo: formato di errore unico per tutto il backend e `Clock` iniettabile.
- Step:
  - `ErroreResponse` (record), `CodiceErrore` (enum con **tutti** i codici e il loro status), `ApplicazioneException`
  - `ExceptionsHandler`: validazione con i `campi`, `ApplicazioneException`, errori imprevisti → 500 generico senza stack
  - `ClockConfig` e costante unica del fuso `Europe/Rome`
  - test dell'handler: validazione, eccezione applicativa, errore imprevisto

### 04 · Frontend · Progetto React e configurazione
- Branch: `chore/frontend-setup` · Dipende da: 01
- Leggi: `frontend/CLAUDE.md` (Stack, Struttura, Design)
- Obiettivo: progetto React + TypeScript pronto, con struttura, stile base e strumenti configurati.
- Step:
  - creare il progetto con Vite (React + TypeScript) in `frontend/`
  - installare solo le dipendenze previste: `bootstrap`, `@reduxjs/toolkit`, `react-redux`, `react-router-dom`,
    `@fontsource/nunito-sans`, Vitest, React Testing Library, `user-event`, jsdom
  - cartelle `src/api`, `store`, `features/*`, `common`, `styles`
  - `.env.example` e `VITE_API_URL` (`.env.local` fuori da Git)
  - `styles/tokens.css` con le variabili di colore e lo stile base dei pulsanti "push"; import di Bootstrap e del font
  - ESLint del template; script `npm test`
  - verificare `npm run dev`, `build`, `lint`, `test`
  - verificare nel browser che la pagina di prova mostri font e colori

### 05 · Frontend · Importazione delle schermate Stitch
- Branch: `chore/frontend-stitch` · Dipende da: 04
- Leggi: `frontend/CLAUDE.md` (Design, "Riferimento grafico")
- Dati: progetto Stitch "QuackLingo Frontend Draft", ID `14737734096977436897`. Schermate:
  `55232b38e67a487b99866f6f63653afb`, `454909794b084f3cae32f5095110e1ed`, `1cd57fbb755b4bc9b0ed0ca5545de576`,
  `4c7960be8b0d4c0ebea302b134a40579`, `0c2426c0ee404f9f82f9316e9dbcd8e9`, `db0e5ed80531446e85a4840d93227c15`,
  `b83f5aa3227a415baa2e1f2bafa4060c`, `b3c78969af104fb984273fbd285d38bb`
- Obiettivo: avere le schermate di riferimento nel repository, ben nominate e collegate alle task che le usano.
- Step:
  - scaricare immagini e codice delle 8 schermate in `frontend/design/stitch/` (strumento Stitch e `curl -L`)
  - dare a ogni schermata un nome chiaro e creare `frontend/design/stitch/README.md` con la tabella:
    schermata → file → schermata prevista → numero della task che la usa
  - elencare le schermate previste che **non** hanno un riferimento (es. verifica email, impostazioni, admin) e dirlo all'utente
  - **non** copiare il codice di Stitch nei componenti: è solo riferimento visivo
- Nota: se lo strumento Stitch non è disponibile, chiedere all'utente di esportare le schermate a mano.

---

# Fase 1 – Backend: accesso (auth)

### 06 · Backend · Utente, Streak e Ruolo
- Branch: `feature/utente-entita` · Dipende da: 02
- Leggi: `progettazione.md` (Utente, Streak, Ruolo), `backend/CLAUDE.md` (Entità, Lombok)
- Obiettivo: entità e repository degli utenti.
- Step:
  - enum `Ruolo`; entità `Utente` con **tutti** i campi della progettazione (nessun `@SQLRestriction`) ed entità `Streak` (1:1, `LocalDate`)
  - `UtenteRepository` (ricerca per email tra gli utenti attivi, esistenza email) e `StreakRepository`
  - test sui repository con H2: salvataggio, ricerca per email, email unica

### 07 · Backend · Sicurezza: JWT e filtro
- Branch: `feature/security-jwt` · Dipende da: 03, 06
- Leggi: `backend/CLAUDE.md` (Sicurezza → Autenticazione e accessi, XSS, CSRF), `specs/auth.md` (Note)
- Obiettivo: autenticazione stateless con JWT e regole di accesso.
- Step:
  - `JwtService` (genera e valida, 24 ore, segreto da configurazione) e `PasswordEncoder` BCrypt
  - filtro: carica l'utente dal database; 401 se cancellato o se il token è precedente a `tokenValidiDal`
  - `SecurityConfig`: stateless, CSRF disattivato, regole di accesso (`/api/auth/**` pubblico, `/api/admin/**` solo admin,
    altro autenticato **con email verificata**, tranne `/api/me`, `/api/me/verification`, `/api/me/verification/resend`)
  - risposte 401 `NON_AUTENTICATO`, 403 `ACCESSO_NEGATO`, 403 `EMAIL_NON_VERIFICATA` nel formato errore unico
  - CORS solo per l'origine del frontend (da configurazione) e header di sicurezza (CSP, nosniff, frame)
  - test (con un controller di prova solo nei test): token valido, scaduto, precedente a `tokenValidiDal`, utente cancellato,
    ruolo non ammesso, email non verificata

### 08 · Backend · Limite richieste per IP
- Branch: `feature/security-limite-ip` · Dipende da: 03, 07
- Leggi: `backend/CLAUDE.md` (Limite per indirizzo IP), `specs/auth.md` (Note)
- Obiettivo: massimo 20 richieste al minuto per IP su `/api/auth/**`.
- Step:
  - filtro con contatori solo in memoria (`ConcurrentHashMap`), pulizia periodica degli IP vecchi, nessuna libreria
  - lettura dell'IP reale da `X-Forwarded-For` (configurazione di Spring per Render)
  - oltre il limite: 429 `TROPPE_RICHIESTE` senza eseguire la richiesta
  - test con `Clock` fisso: 20 richieste ok, la 21ª → 429, finestra scaduta → ok, IP diversi indipendenti

### 09 · Backend · Email: servizio e template
- Branch: `feature/mail-servizio` · Dipende da: 02
- Leggi: `backend/CLAUDE.md` (Stack, Configurazione, XSS), `specs/auth.md` (Codici email)
- Obiettivo: invio delle email dei codici, con implementazione diversa per profilo.
- Step:
  - interfaccia `MailService` con tre implementazioni: **log** (locale; mai in `prod`), **SMTP Gmail** (locale, opzionale),
    **Brevo via API HTTP** (`prod`, con `java.net.http.HttpClient`, nessuna dipendenza nuova)
  - template Thymeleaf (solo `th:text`) per: verifica email, reset password, cambio email
  - chiavi necessarie in `env.properties.example`
  - test: rendering dei template e servizio su log

### 10 · Backend · Codici di verifica
- Branch: `feature/auth-codici` · Dipende da: 06, 09
- Leggi: `specs/auth.md` (Codici email, Verifica di un codice), `progettazione.md` (CodiceVerifica, TipoCodice)
- Obiettivo: generazione e verifica dei codici a 6 cifre, riusabile da registrazione, reset e cambio email.
- Step:
  - `TipoCodice`, entità `CodiceVerifica`, repository
  - generazione: 6 cifre con `SecureRandom`, hash BCrypt, 15 minuti, invalida i precedenti dello stesso tipo,
    60 secondi tra due invii (`ATTENDI_REINVIO`)
  - verifica nell'ordine della spec: `CODICE_NON_VALIDO`, `CODICE_SCADUTO`, `TROPPI_TENTATIVI`, conteggio tentativi
  - test con `Clock` fisso su tutti i casi della sezione "Codici" di `auth.md`

### 11 · Backend · Registrazione
- Branch: `feature/auth-registrazione` · Dipende da: 03, 06, 07, 08, 10
- Leggi: `specs/auth.md` (Registrazione), `backend/CLAUDE.md` (DTO, Password)
- Obiettivo: `POST /api/auth/register`.
- Step:
  - `RegistrazioneRequest` (pulizia nel costruttore compatto, validazioni, regole password) e `TokenResponse`
  - service e controller (201 con il solo token)
  - 409 `EMAIL_GIA_USATA` con il messaggio indicato nella spec
  - creazione della `Streak` a zero; invio del codice di verifica; un errore d'invio **non** annulla la registrazione
  - test service (Mockito) e controller (MockMvc, H2)

### 12 · Backend · Login con blocco dell'account
- Branch: `feature/auth-login` · Dipende da: 11
- Leggi: `specs/auth.md` (Login, Contatori dei login falliti)
- Obiettivo: `POST /api/auth/login` con protezione da tentativi ripetuti.
- Step:
  - `LoginRequest`; messaggio unico per ogni fallimento (401 `CREDENZIALI_NON_VALIDE`), anche ad account bloccato
  - contatori e blocco: 3 errori entro 10 secondi dal primo oppure 5 totali → blocco di 15 minuti
  - login riuscito azzera i contatori; ammesso anche con email non verificata
  - test: tutti i casi della sezione "Contatori" di `auth.md`

### 13 · Backend · Verifica email e nuovo codice
- Branch: `feature/utente-verifica-email` · Dipende da: 10, 11
- Leggi: `specs/auth.md` (Verifica email, Nuovo codice di verifica)
- Obiettivo: `POST /api/me/verification` e `POST /api/me/verification/resend`.
- Step:
  - endpoint nel package `utente`, accessibili anche con email non verificata
  - già verificata → 204 senza modifiche; reinvio prima di 60 secondi → 429 `ATTENDI_REINVIO`
  - test: codice giusto/sbagliato/scaduto, tentativi, reinvio, email già verificata

### 14 · Backend · Password dimenticata e reimpostazione
- Branch: `feature/auth-password-reset` · Dipende da: 10, 11
- Leggi: `specs/auth.md` (Password dimenticata, Reimpostazione password)
- Obiettivo: `POST /api/auth/password/forgot` e `POST /api/auth/password/reset`.
- Step:
  - `forgot`: sempre 200 con messaggio generico; il codice parte solo se l'utente esiste ed è passato il limite di 60 secondi (senza 429)
  - `reset`: email inesistente → 400 `CODICE_NON_VALIDO`; codice giusto → nuova password, email verificata, logout ovunque, login sbloccato
  - test: email esistente e inesistente con la stessa risposta, reset completo, token precedenti rifiutati

### 15 · Backend · Seeder del superadmin
- Branch: `feature/seed-superadmin` · Dipende da: 06, 07
- Leggi: `backend/CLAUDE.md` (Seeder), `specs/admin.md` (Cambio ruolo)
- Obiettivo: all'avvio esiste sempre un solo SUPERADMIN, creato dalle variabili d'ambiente.
- Step:
  - **chiedere all'utente** nome e cognome del superadmin (proposta: valori fissi, email e password da `env.properties`)
  - `CommandLineRunner` in `seed`: se non esiste un SUPERADMIN lo crea (email già verificata) con la sua `Streak`
  - aggiungere le chiavi a `env.properties.example`
  - test: primo avvio crea, secondo avvio non duplica

---

# Fase 2 – Backend: modelli e profilo

### 16 · Backend · Entità dei contenuti
- Branch: `feature/contenuti-entita` · Dipende da: 02
- Leggi: `progettazione.md` (Tappa, Lezione, Domanda, Opzione, ElementoAbbinamento, enum), `backend/CLAUDE.md` (Soft delete, Entità)
- Obiettivo: modello dei contenuti con soft delete.
- Step:
  - enum `TipoDomanda`, `Lato`; entità `Tappa`, `Lezione`, `Domanda` (con `@SQLDelete` + `@SQLRestriction`), `Opzione`, `ElementoAbbinamento` (self-reference)
  - **nessun** vincolo UNIQUE sull'`ordine` (lo garantisce il service)
  - repository con le query di base
  - test sui repository: un elemento cancellato non compare più nelle query normali

### 17 · Backend · Entità dei tentativi
- Branch: `feature/tentativi-entita` · Dipende da: 06, 16
- Leggi: `progettazione.md` (TentativoLezione, RispostaOpzione, RispostaAbbinamento, EsitoTentativo)
- Obiettivo: modello dei tentativi e delle risposte.
- Step:
  - enum `EsitoTentativo`; entità `TentativoLezione`, `RispostaOpzione` (UNIQUE tentativo + domanda, campo `risolta`), `RispostaAbbinamento` (con `domanda_id` e `risolta`)
  - repository, compresa la query "lezioni attive con almeno un tentativo SUPERATO di un utente"
  - test sui repository

### 18 · Backend · Profilo: GET /api/me
- Branch: `feature/utente-profilo` · Dipende da: 06, 07, 17
- Leggi: `specs/me.md` (Regole comuni, Dati del profilo, Streak calcolata in lettura)
- Obiettivo: `GET /api/me`.
- Step:
  - `MeResponse` con il metodo `da(...)`: nome, cognome, email, ruolo, fotoProfilo, emailVerificata, streak (valore e record), lezioniCompletate
  - streak calcolata in lettura (0 se l'ultima attività non è né oggi né ieri; il record non cambia; il dato salvato non si modifica)
  - accessibile anche con email non verificata
  - test: streak oggi/ieri/scaduta, conteggio lezioni (solo attive, una sola volta per lezione), utente non verificato

---

# Fase 3 – Frontend: accesso

### 19 · Frontend · Sessione, client API e rotte protette
- Branch: `feature/frontend-sessione` · Dipende da: 04, 07, 18
- Leggi: `frontend/CLAUDE.md` (Chiamate, Errori, Sicurezza), `specs/auth.md`, `specs/me.md`
- Obiettivo: base tecnica per tutte le schermate.
- Step:
  - modulo `api` (`fetch`, `VITE_API_URL`, header `Authorization`, `ApiError` con `codice`/`messaggio`/`campi`)
  - file unico che traduce i `codice` in messaggi italiani
  - slice Redux della sessione: token in `localStorage`, caricamento di `/api/me` all'avvio, logout (cancella token e stato)
  - 401 → logout e login; 403 `EMAIL_NON_VERIFICATA` → schermata del codice
  - guardie delle rotte: autenticato → email verificata → admin
  - test: traduzione errori, comportamento su 401/403, logout
  - verifica nel browser

### 20 · Frontend · Registrazione e login
- Branch: `feature/frontend-auth` · Dipende da: 11, 12, 19
- Leggi: `frontend/CLAUDE.md`, `specs/auth.md` (Registrazione, Login), schermate Stitch in `frontend/design/stitch/`
- Obiettivo: schermate di registrazione e di accesso.
- Step:
  - form di registrazione con validazione dei campi, errori per campo (`campi`), gestione di `EMAIL_GIA_USATA` con rimando al "Password dimenticata"
  - form di login con il messaggio unico del backend; dopo il login: email non verificata → schermata del codice, altrimenti mappa
  - disattivare il pulsante durante la richiesta; gestire 429
  - test delle interazioni (doppio click che invia una sola richiesta)
  - verifica nel browser: registrazione, login, errori

### 21 · Frontend · Verifica email e password dimenticata
- Branch: `feature/frontend-verifica-reset` · Dipende da: 13, 14, 19
- Leggi: `frontend/CLAUDE.md`, `specs/auth.md` (Verifica email, Nuovo codice, Password dimenticata, Reimpostazione)
- Obiettivo: schermate del codice e del recupero password.
- Step:
  - schermata del codice (6 cifre) con "Invia di nuovo" (pulsante disattivato per 60 secondi, gestione di `ATTENDI_REINVIO`), messaggi per `CODICE_NON_VALIDO`, `CODICE_SCADUTO`, `TROPPI_TENTATIVI`
  - "Password dimenticata": richiesta con messaggio sempre generico; poi schermata con email, codice e nuova password
  - dopo il reset: logout ovunque e rimando al login
  - verifica nel browser (in locale il codice si legge nel log del backend)

### 22 · Frontend · Struttura dell'app e navigazione
- Branch: `feature/frontend-layout` · Dipende da: 19
- Leggi: `frontend/CLAUDE.md` (Struttura, Design), schermate Stitch
- Obiettivo: guscio dell'applicazione mobile first.
- Step:
  - layout principale (barra di navigazione, contenitore a ~390 px, adattamento desktop), componenti comuni: pulsante "push", card, drawer, messaggio di errore
  - rotte vuote per mappa, profilo, admin
  - verifica nel browser a larghezza mobile e desktop

---

# Fase 4 – Profilo

### 23 · Backend · Modifica dati e cambio password
- Branch: `feature/utente-modifica` · Dipende da: 18
- Leggi: `specs/me.md` (Modifica dati, Cambio password)
- Obiettivo: `PATCH /api/me` e `PATCH /api/me/password`.
- Step:
  - modifica di nome e cognome (obbligatori, `strip()`, massimo 50 caratteri)
  - cambio password: `PASSWORD_ATTUALE_ERRATA`, nuova password valida e diversa dalla vecchia, logout ovunque
  - test di tutti i casi

### 24 · Backend · Foto profilo
- Branch: `feature/utente-avatar` · Dipende da: 18
- Leggi: `specs/me.md` (Foto profilo), `backend/CLAUDE.md` (Stack, Configurazione)
- Obiettivo: `PATCH /api/me/avatar` con Cloudinary.
- Step:
  - **L'utente crea l'account Cloudinary gratuito** e inserisce le chiavi in `env.properties` (e nell'`.example`, vuote)
  - upload multipart: jpg/png/webp, massimo 2 MB, tipo verificato sul contenuto reale; errori `FILE_NON_VALIDO`
  - nome fisso `avatar/<id utente>` (la nuova foto sostituisce la vecchia); salvataggio dell'URL https
  - errore di Cloudinary → 500 `ERRORE_INTERNO`, foto precedente invariata
  - test con Cloudinary simulato (nessuna chiamata reale nei test)

### 25 · Backend · Cambio email
- Branch: `feature/utente-cambio-email` · Dipende da: 10, 18
- Leggi: `specs/me.md` (Cambio email)
- Obiettivo: `POST /api/me/email` e `POST /api/me/email/confirm`.
- Step:
  - richiesta: controlli nell'ordine della spec (password, formato, uguale all'attuale, già usata, 60 secondi); codice alla **nuova** email
  - conferma: verifica codice, nuovo controllo che l'email sia libera, email sostituita, `email_verificata = true`, logout ovunque
  - test di tutti i casi

### 26 · Backend · Cancellazione account
- Branch: `feature/utente-cancellazione` · Dipende da: 18, 24
- Leggi: `specs/me.md` (Cancellazione account)
- Obiettivo: `DELETE /api/me` con anonimizzazione.
- Step:
  - password richiesta; il SUPERADMIN non può cancellarsi (403 `ACCESSO_NEGATO`)
  - anonimizzazione dei dati, `deleted_at`, logout ovunque; `Streak` e `CodiceVerifica` eliminati; tentativi conservati
  - foto cancellata da Cloudinary (se fallisce: log, l'anonimizzazione prosegue)
  - test: dati anonimizzati, login impossibile, email di nuovo registrabile, tentativi ancora presenti

### 27 · Frontend · Profilo e modifica dati
- Branch: `feature/frontend-profilo` · Dipende da: 22, 23
- Leggi: `frontend/CLAUDE.md` (Profilo), `specs/me.md`, schermate Stitch
- Obiettivo: schermata del profilo.
- Step:
  - avatar, nome, cognome, email, etichetta del ruolo; solo streak attuale, record e lezioni completate
  - modifica di nome e cognome; cambio password (poi logout e rimando al login)
  - verifica nel browser

### 28 · Frontend · Avatar, cambio email e cancellazione account
- Branch: `feature/frontend-impostazioni` · Dipende da: 24, 25, 26, 27
- Leggi: `frontend/CLAUDE.md` (Profilo → Avatar), `specs/me.md`
- Obiettivo: le tre azioni sul proprio account.
- Step:
  - avatar: `accept="image/*"` senza `capture`, ridimensionamento a circa 512×512 su canvas (jpeg/webp, sotto i 2 MB), anteprima, gestione di `FILE_NON_VALIDO`
  - cambio email in due passaggi (richiesta, codice), poi logout
  - cancellazione con richiesta di password e conferma esplicita, poi logout
  - test sul ridimensionamento e sulle interazioni
  - verifica nel browser (anche con una foto grande)

---

# Fase 5 – Amministrazione e contenuti

### 29 · Backend · Admin: utenti
- Branch: `feature/admin-utenti` · Dipende da: 06, 07
- Leggi: `specs/admin.md` (Regole comuni, Utenti)
- Obiettivo: `GET /api/admin/users` e `PATCH /api/admin/users/{id}/role`.
- Step:
  - lista paginata (`page`, `size` massimo 50) con ricerca `q`, solo utenti attivi, dal più recente; `PaginaResponse`
  - cambio ruolo nell'ordine dei controlli della spec e con la tabella dei permessi; logout ovunque solo se cambia davvero
  - test: ogni cella della tabella, nessuno modifica se stesso, SUPERADMIN intoccabile, token precedenti rifiutati

### 30 · Backend · Admin: tappe e lezioni
- Branch: `feature/admin-tappe-lezioni` · Dipende da: 16, 07
- Leggi: `specs/admin.md` (Ordine, Tappe, Lezioni)
- Obiettivo: gestione di tappe e lezioni.
- Step:
  - elenco, creazione (in fondo), modifica (titolo, descrizione, ordine), dettaglio lezione, cancellazione
  - ordine gestito nel service: 1…n senza buchi, riordino con spostamento, rinumerazione dopo la cancellazione; posizione fuori limite → 400
  - soft delete a cascata (tappa → lezioni → domande)
  - lunghezze massime e validazioni
  - test: creazione, riordino su e giù, rinumerazione, cascata

### 31 · Backend · Admin: domande
- Branch: `feature/admin-domande` · Dipende da: 30
- Leggi: `specs/admin.md` (Domande)
- Obiettivo: creazione, modifica e cancellazione delle domande.
- Step:
  - creazione per tipo: VERO_FALSO (`rispostaVero`, opzioni "Vero"/"Falso" create dal backend), SCELTA_MULTIPLA (2–6 opzioni, una sola corretta), ABBINAMENTO (2–6 coppie)
  - modifica dei **soli testi** (id che devono appartenere alla domanda; opzioni di VERO_FALSO non modificabili)
  - cancellazione (soft) con rinumerazione; si può cancellare l'ultima domanda di una lezione
  - risposte admin con soluzioni
  - test: tre tipi validi, regole violate, modifica testi, tentativi di cambiare la struttura

### 32 · Frontend · Admin: struttura e gestione utenti
- Branch: `feature/frontend-admin-utenti` · Dipende da: 22, 29
- Leggi: `frontend/CLAUDE.md` (Admin), `specs/admin.md` (Utenti), schermate Stitch
- Obiettivo: pannello admin e sezione utenti.
- Step:
  - layout admin: sidebar su desktop, barra in basso (Utenti, Contenuti) su mobile
  - lista utenti: tabella su desktop, card su mobile; ricerca `q` e paginazione
  - cambio ruolo con azioni nascoste quando non consentite e gestione del 403
  - verifica nel browser (desktop e mobile, con admin e superadmin)

### 33 · Frontend · Admin: tappe e lezioni
- Branch: `feature/frontend-admin-contenuti` · Dipende da: 30, 32
- Leggi: `frontend/CLAUDE.md` (Admin), `specs/admin.md` (Tappe, Lezioni)
- Obiettivo: gestione di tappe e lezioni.
- Step:
  - elenco con accordion (mobile) e colonna sinistra (desktop); creazione, modifica, cancellazione con conferma
  - riordino con pulsanti Su / Giù
  - verifica nel browser

### 34 · Frontend · Admin: editor delle domande
- Branch: `feature/frontend-admin-domande` · Dipende da: 31, 33
- Leggi: `frontend/CLAUDE.md` (Admin), `specs/admin.md` (Domande)
- Obiettivo: creare e modificare domande.
- Step:
  - form di creazione per tipo (vero/falso, scelta multipla con spunta della corretta, coppie dell'abbinamento)
  - modifica dei soli testi; per cambiare la struttura: cancellare e ricreare (spiegato nell'interfaccia)
  - cancellazione con conferma
  - verifica nel browser

### 35 · Docs/Deploy · Contenuti iniziali delle lezioni
- Branch: `feature/seed-contenuti` · Dipende da: 31
- Leggi: `backend/CLAUDE.md` (Seeder), `specs/admin.md` (Domande)
- Obiettivo: percorso di partenza pronto per la demo.
- Step:
  - **concordare con l'utente la dimensione**: proposta 3 tappe, 3 lezioni ciascuna, 5–6 domande a lezione, con tutti e tre i tipi
  - Claude scrive i contenuti (teoria, anatomia di base, tecnica, falsi miti) con spiegazioni brevi e corrette, in italiano
  - **l'utente rilegge e corregge ogni testo** prima del commit (obbligatorio)
  - seeder in Java che inserisce i contenuti solo se non esiste nessuna tappa, rispettando le regole di struttura
  - test: primo avvio inserisce, secondo avvio no

---

# Fase 6 – Percorso e lezione

### 36 · Backend · Mappa del percorso
- Branch: `feature/percorso-mappa` · Dipende da: 17, 16, 07
- Leggi: `specs/percorso.md` (Stato delle lezioni, Mappa)
- Obiettivo: `GET /api/path/stages`.
- Step:
  - risposta con tappe e lezioni attive; tappe senza lezioni escluse; nessun numero di domande
  - stato per utente calcolato (`COMPLETATA`, `SBLOCCATA`, `BLOCCATA`) con sblocco sequenziale su tutto il percorso
  - test: nuovo utente, completamento, passaggio tra tappe, lezione cancellata, tentativo FALLITO

### 37 · Backend · Contenuto della lezione
- Branch: `feature/percorso-lezione` · Dipende da: 36
- Leggi: `specs/percorso.md` (Contenuto di una lezione)
- Obiettivo: `GET /api/path/lessons/{id}`.
- Step:
  - errori: 404 (inesistente, cancellata, senza domande), 403 `LEZIONE_BLOCCATA`
  - domande **mescolate**, opzioni della scelta multipla mescolate, abbinamento con entrambe le colonne mescolate; VERO_FALSO in ordine fisso
  - la risposta non contiene mai `corretta`, `abbinamento_corretto_id` né spiegazioni
  - test: casi di errore, assenza delle soluzioni, tutti gli elementi presenti

### 38 · Backend · Avvio del tentativo e risposte
- Branch: `feature/tentativi-risposte` · Dipende da: 17, 37
- Leggi: `specs/tentativi.md` (Regole generali, Avvio, Risposta)
- Obiettivo: `POST /api/attempts` e `POST /api/attempts/{id}/answers`.
- Step:
  - avvio con i controlli in ordine; chiude come FALLITO un tentativo IN_CORSO sulla stessa lezione
  - risposta: validazioni (domanda della lezione, opzione della domanda, coppie complete), valutazione per tipo, errori (1 per coppia sbagliata), `MAX_ERRORI = 3`
  - salvataggio della sola prima risposta, campo `risolta`, più risposte alla stessa domanda ammesse finché non risolta
  - risposta del server: `corretta`, `soluzione`, `spiegazione`, `errori`, `esito`
  - 409 `TENTATIVO_CHIUSO`, 404 per tentativo altrui
  - contenuti modificati durante il tentativo (le domande del tentativo sono quelle attive e create prima di `iniziato_at`): risposta a una domanda aggiunta dopo l'avvio → 400 `VALIDAZIONE`; a una domanda cancellata → 404 `NON_TROVATO`; lezione cancellata → 404
  - test su tutti i casi della sezione "Test da prevedere"

### 39 · Backend · Chiusura del tentativo e streak
- Branch: `feature/tentativi-chiusura` · Dipende da: 38, 18
- Leggi: `specs/tentativi.md` (Chiusura), `specs/me.md` (Streak calcolata in lettura)
- Obiettivo: `POST /api/attempts/{id}/complete`.
- Step:
  - chiusura possibile solo se tutte le domande del tentativo (attive e create prima dell'avvio) hanno `risolta = true` (altrimenti 400 `VALIDAZIONE`); già chiuso → 409
  - una domanda aggiunta dopo l'avvio è ignorata dalla chiusura; una domanda cancellata non conta per la chiusura (le risposte a queste domande si gestiscono nella task 38)
  - esito SUPERATO e aggiornamento della streak (oggi invariata, ieri +1, altrimenti 1, record aggiornato)
  - risposta con `esito`, `errori`, streak aggiornata
  - test con `Clock` fisso: tutte le casistiche di streak, FALLITO non tocca la streak, lezione successiva sbloccata

### 40 · Backend · Resoconto del tentativo
- Branch: `feature/tentativi-resoconto` · Dipende da: 39
- Leggi: `specs/tentativi.md` (Resoconto)
- Obiettivo: `GET /api/attempts/{id}`.
- Step:
  - risposta con esito, errori, date e domande già risposte (prima risposta, correttezza, soluzione, spiegazione) nell'ordine dell'admin
  - funziona anche per tentativi IN_CORSO e per domande o lezioni cancellate dopo il tentativo (verificare l'effetto di `@SQLRestriction`; se complicato, fermarsi e chiedere)
  - solo il proprietario (404 altrimenti)
  - test: tentativo chiuso e in corso, contenuti cancellati, tentativo altrui

### 41 · Frontend · Mascotte Coach Quack
- Branch: `feature/frontend-mascotte` · Dipende da: 22
- Leggi: `frontend/CLAUDE.md` (Design → Direzione visiva), schermate Stitch
- Obiettivo: componente della mascotte, solo frontend.
- Step:
  - papero atletico con fascia rossa in **SVG** con animazioni **CSS** (nessuna immagine esterna né libreria)
  - stati: neutro, contento (vittoria), triste (sconfitta)
  - rispettare `prefers-reduced-motion`
  - verifica nel browser

### 42 · Frontend · Mappa del percorso
- Branch: `feature/frontend-mappa` · Dipende da: 36, 22
- Leggi: `frontend/CLAUDE.md` (Mappa), `specs/percorso.md`, schermate Stitch
- Obiettivo: schermata della mappa.
- Step:
  - tappe e nodi in verticale con i tre stati (verde con spunta, gialla attiva, grigia con lucchetto)
  - tocco su una lezione sbloccata o completata → avvio; bloccata non avvia (gestire comunque `LEZIONE_BLOCCATA`)
  - gestione di caricamento ed errori
  - verifica nel browser con un utente nuovo e con uno avanzato

### 43 · Frontend · Lezione: flusso, vero/falso e scelta multipla
- Branch: `feature/frontend-lezione` · Dipende da: 37, 38, 39, 42
- Leggi: `frontend/CLAUDE.md` (Lezione), `specs/percorso.md`, `specs/tentativi.md`, schermate Stitch
- Obiettivo: cuore dell'app, senza l'abbinamento.
- Step:
  - slice Redux del tentativo (coda di domande, risposte, errori, esito, timer di durata)
  - barra superiore: chiusura, avanzamento, indicatore **"Errori x/3"**, `MAX_ERRORI = 3` come costante
  - domande VERO_FALSO e SCELTA_MULTIPLA; invio di una risposta alla volta, pulsante disattivato durante la richiesta
  - drawer di esito (verde/rosso) con spiegazione; riproposta delle domande sbagliate a fine giro
  - esito `FALLITO` → schermata di sconfitta con "Riprova"; tutte risolte → `complete`
  - test: doppio click invia una sola risposta, nessuna azione dopo l'esito, coda e riproposta, terzo errore
  - verifica nel browser: lezione completa, lezione fallita, chiusura a metà

### 44 · Frontend · Lezione: abbinamento
- Branch: `feature/frontend-abbinamento` · Dipende da: 43
- Leggi: `frontend/CLAUDE.md` (Lezione), `specs/tentativi.md` (Risposta), schermate Stitch
- Obiettivo: domande di tipo ABBINAMENTO.
- Step:
  - tessere sinistra ↔ destra accoppiabili con il tocco, possibilità di cambiare un collegamento
  - invio di **tutte le coppie insieme** solo quando sono complete
  - esito per coppia nel drawer; riproposta in caso di errore
  - test sulle interazioni (doppio tocco, coppie incomplete)
  - verifica nel browser (touch e mouse)

### 45 · Frontend · Vittoria, sconfitta e streak
- Branch: `feature/frontend-vittoria` · Dipende da: 41, 43
- Leggi: `frontend/CLAUDE.md` (Vittoria), `specs/tentativi.md` (Chiusura)
- Obiettivo: schermate di fine lezione.
- Step:
  - vittoria con Coach Quack, streak e record dalla risposta, "+1 OGGI" solo se il valore è aumentato
  - tracker della settimana L-M-M-G-V-S-D dedotto dal valore della streak
  - riquadro: esito, errori (x/3), tempo impiegato (timer nel frontend)
  - pulsanti "Continua" (mappa) e "Rivedi risposte"
  - test sul calcolo del tracker
  - verifica nel browser

### 46 · Frontend · Resoconto della lezione
- Branch: `feature/frontend-resoconto` · Dipende da: 40, 45
- Leggi: `frontend/CLAUDE.md`, `specs/tentativi.md` (Resoconto)
- Obiettivo: schermata "Rivedi risposte".
- Step:
  - elenco delle domande con la prima risposta, correttezza, soluzione e spiegazione
  - gestione del tentativo non trovato o di un altro utente
  - verifica nel browser

---

# Fase 7 – Rifinitura e consegna

### 47 · Docs/Deploy · Responsive e prova su telefono
- Branch: `fix/frontend-rifinitura` · Dipende da: 46, 34
- Leggi: `frontend/CLAUDE.md` (Design)
- Obiettivo: app comoda su telefono vero e su desktop.
- Step:
  - controllare tutte le schermate a ~390 px e a larghezze desktop; correggere overflow, tocchi piccoli, contrasti
  - provare su un telefono reale (rete locale o deploy) incluse foto profilo da fotocamera e galleria
  - verificare caricamento, errori di rete e stati vuoti

### 48 · Docs/Deploy · Verifica della sicurezza
- Branch: `chore/verifica-sicurezza` · Dipende da: 46, 34
- Leggi: `backend/CLAUDE.md` (Sicurezza), `frontend/CLAUDE.md` (Sicurezza)
- Obiettivo: controllo finale contro SQL injection, CSRF e XSS e sulla gestione dei segreti.
- Step:
  - cercare nel codice query costruite con concatenazione, `th:utext`, `dangerouslySetInnerHTML`, `innerHTML`
  - provare a mano: login a raffica (blocco), 21 richieste in un minuto (429), token precedente a `tokenValidiDal`, un utente su `/api/admin/**`
  - controllare gli header di sicurezza e il CORS (solo origine del frontend)
  - verificare che nessun segreto sia tracciato in Git (`env.properties`, `.env*`)
  - riportare i risultati e correggere i problemi in task separate

### 49 · Docs/Deploy · Deploy di database e backend
- Branch: `chore/deploy-backend` · Dipende da: 48
- Leggi: `backend/CLAUDE.md` (Configurazione, profilo `prod`)
- Obiettivo: backend online.
- Step:
  - **l'utente crea gli account** (Neon, Render, Brevo) e inserisce le variabili sulle piattaforme (mai nel repository)
  - `Dockerfile` per Render con limite di memoria della JVM (512 MB)
  - profilo `prod`: database Neon, JWT, CORS, Cloudinary, Brevo, superadmin
  - verificare avvio, seeder, registrazione con email reale e login
  - annotare che Render gratuito si addormenta dopo 15 minuti: aprire l'app prima della presentazione

### 50 · Docs/Deploy · Deploy del frontend
- Branch: `chore/deploy-frontend` · Dipende da: 49
- Leggi: `frontend/CLAUDE.md` (Comandi, variabili d'ambiente)
- Obiettivo: frontend online collegato al backend.
- Step:
  - **l'utente crea l'account** Vercel o Netlify e configura `VITE_API_URL`
  - configurare le rotte (rewrite verso `index.html`)
  - aggiornare il CORS del backend con l'origine reale
  - provare il flusso completo online: registrazione, verifica email, lezione, streak, pannello admin

### 51 · Docs/Deploy · README, licenza e copyright
- Branch: `docs/readme-licenza` · Dipende da: 50
- Obiettivo: documentazione di consegna e tutela del lavoro.
- Step:
  - `README.md`: descrizione, funzionalità, stack, come avviare backend e frontend in locale, variabili d'ambiente, link al sito online
  - file `LICENSE` ("Tutti i diritti riservati" o licenza scelta dall'utente) e avviso `© 2026 Nome Cognome` nel `README` e nel footer
  - controllare che il repository sia **privato** fino alla consegna
  - aggiornare `progettazione.md` e le spec se qualcosa è cambiato in corso d'opera

### 52 · Docs/Deploy · Preparazione all'esame
- Branch: `docs/esame` · Dipende da: 51
- Obiettivo: arrivare alla presentazione finale sapendo spiegare ogni scelta.
- Step:
  - **l'utente comunica le regole dell'esame** (durata, formato, cosa consegnare)
  - scaletta di presentazione e demo (percorso completo: registrazione, lezione, streak, pannello admin)
  - elenco delle scelte da saper spiegare: architettura per funzionalità, sicurezza (JWT, blocco login, codici email), soft delete e anonimizzazione, regole di gioco, deploy
  - domande probabili con risposte brevi
  - prova generale: aprire l'app online prima, controllare che sia sveglia
