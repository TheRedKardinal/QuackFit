# QuackFit – Progettazione

> Convenzioni: tutti gli id sono UUID; tutte le date sono Instant (TIMESTAMP in DB), tranne dove indicato LocalDate.
> Formato endpoint: METODO "path" → status | chi può chiamarlo | descrizione.
> Le regole di dettaglio di ogni funzionalità sono in `specs/`.

## Entità

### Funzionalità `utente`

#### Utente
* id UUID (PK)
* nome String
* cognome String
* email String (UNIQUE)
* password String (hash BCrypt)
* foto_profilo String (URL Cloudinary, nullable)
* ruolo Ruolo (ENUM, default UTENTE)
* email_verificata boolean (default false)
* token_validi_dal Instant (i token emessi prima di questa data vengono rifiutati: "logout ovunque")
* login_falliti int (default 0)
* primo_login_fallito_at Instant (nullable; inizio della serie di login falliti)
* bloccato_fino Instant (nullable; account bloccato fino a questa data)
* created_at Instant
* deleted_at Instant (null = account attivo; valorizzato con l'anonimizzazione)

#### Streak
* id UUID (PK)
* valore int (giorni consecutivi, come salvato all'ultimo aggiornamento)
* record int (miglior streak di sempre)
* ultima_attivita LocalDate (giorno, fuso Europe/Rome, dell'ultima lezione superata; nullable)
* utente_id UUID (FK → Utente, 1:1, UNIQUE)
* updated_at Instant

### Funzionalità `auth`

#### CodiceVerifica
* id UUID (PK)
* tipo TipoCodice (ENUM)
* codice_hash String (hash BCrypt del codice a 6 cifre)
* scadenza Instant (creazione + 15 minuti)
* tentativi_falliti int (default 0, massimo 5)
* usato_at Instant (null = ancora valido; valorizzato quando viene usato o invalidato da un codice più recente)
* nuova_email String (solo per CAMBIO_EMAIL, altrimenti null)
* utente_id UUID (FK → Utente, N:1)
* created_at Instant

### Funzionalità `contenuti`

#### Tappa
* id UUID (PK)
* titolo String
* descrizione String
* ordine int (1, 2, 3… senza buchi; nessun vincolo UNIQUE nel database, gestito nel service)
* created_at Instant
* deleted_at Instant (null = attiva; soft delete)

#### Lezione
* id UUID (PK)
* titolo String
* descrizione String
* ordine int (1, 2, 3… per tappa, senza buchi; nessun vincolo UNIQUE nel database, gestito nel service)
* tappa_id UUID (FK → Tappa, N:1)
* created_at Instant
* deleted_at Instant (null = attiva; soft delete)

#### Domanda
* id UUID (PK)
* testo String
* spiegazione String (mostrata solo dopo la risposta)
* tipo TipoDomanda (ENUM)
* ordine int (1, 2, 3… per lezione, senza buchi; nessun vincolo UNIQUE nel database, gestito nel service; assegnato in creazione, le domande non si riordinano)
* lezione_id UUID (FK → Lezione, N:1)
* created_at Instant
* deleted_at Instant (null = attiva; soft delete)

#### Opzione  (per VERO_FALSO e SCELTA_MULTIPLA)
* id UUID (PK)
* testo String
* corretta boolean (una sola opzione corretta per domanda, vincolo gestito nel service)
* ordine int
* domanda_id UUID (FK → Domanda, N:1)

#### ElementoAbbinamento  (per ABBINAMENTO)
* id UUID (PK)
* testo String
* lato Lato (ENUM)
* abbinamento_corretto_id UUID (FK → ElementoAbbinamento, self-reference, UNIQUE,
  valorizzato solo sugli elementi SINISTRA; null sui DESTRA)
* ordine int (posizione della coppia nella domanda: uguale per l'elemento SINISTRA e il suo DESTRA)
* domanda_id UUID (FK → Domanda, N:1)

### Funzionalità `tentativi`

#### TentativoLezione
* id UUID (PK)
* iniziato_at Instant
* finito_at Instant (null = tentativo in corso)
* esito EsitoTentativo (ENUM, default IN_CORSO)
* errori int (default 0; al terzo errore il tentativo diventa FALLITO)
* utente_id UUID (FK → Utente, N:1)
* lezione_id UUID (FK → Lezione, N:1)

#### RispostaOpzione  (risposte a VERO_FALSO e SCELTA_MULTIPLA, per il resoconto di fine lezione)
* id UUID (PK)
* tentativo_id UUID (FK → TentativoLezione, N:1)
* domanda_id UUID (FK → Domanda, N:1)
* opzione_id UUID (FK → Opzione, N:1)
  (solo la prima risposta a ogni domanda; UNIQUE su tentativo_id + domanda_id)
* risolta boolean (true se la prima risposta era giusta oppure se la domanda è stata poi ripetuta correttamente)

#### RispostaAbbinamento  (risposte agli ABBINAMENTI, per il resoconto di fine lezione)
* id UUID (PK)
* tentativo_id UUID (FK → TentativoLezione, N:1)
* domanda_id UUID (FK → Domanda, N:1)
* elemento_sinistro_id UUID (FK → ElementoAbbinamento, N:1)
* elemento_destro_id UUID (FK → ElementoAbbinamento, N:1)
  (una riga per ogni coppia collegata alla prima risposta)
* risolta boolean (come in RispostaOpzione; lo stesso valore su tutte le righe della stessa domanda)

## Enum

#### Ruolo  (utente)
* UTENTE
* ADMIN
* SUPERADMIN (uno solo, vincolo gestito nel service)

#### TipoCodice  (auth)
* VERIFICA_EMAIL
* RESET_PASSWORD
* CAMBIO_EMAIL

#### TipoDomanda  (contenuti)
* VERO_FALSO
* SCELTA_MULTIPLA (più opzioni elencate, una sola corretta)
* ABBINAMENTO

#### Lato  (contenuti)
* SINISTRA
* DESTRA

#### EsitoTentativo  (tentativi)
* IN_CORSO
* SUPERATO
* FALLITO

## Relazioni
* Utente 1:1 Streak
* Utente 1:N CodiceVerifica
* Utente 1:N TentativoLezione
* Tappa 1:N Lezione
* Lezione 1:N Domanda
* Lezione 1:N TentativoLezione
* Domanda 1:N Opzione
* Domanda 1:N ElementoAbbinamento
* ElementoAbbinamento (SINISTRA) 1:1 ElementoAbbinamento (DESTRA)
* TentativoLezione 1:N RispostaOpzione
* TentativoLezione 1:N RispostaAbbinamento

## Endpoint

#### Auth  (`auth`, spec: specs/auth.md)
@RequestMapping "/api/auth"
* POST "/register"        → 201 Created    | Pubblico | registrazione; invia il codice di verifica email; risponde con il token JWT
* POST "/login"           → 200 OK         | Pubblico | login; risponde con il token JWT
* POST "/password/forgot" → 200 OK         | Pubblico | password dimenticata: invia un codice se l'email esiste; risposta sempre generica
* POST "/password/reset"  → 204 No Content | Pubblico | nuova password con il codice ricevuto; body: email, codice, nuova password; logout ovunque

#### Me  (`utente`, spec: specs/me.md)
@RequestMapping "/api/me"
* GET                          → 200 OK         | Utente autenticato | dati dell'utente loggato: nome, cognome, email, ruolo, foto profilo, email verificata, streak attuale, record, lezioni completate
* PATCH                        → 204 No Content | Utente autenticato | modifica di nome e cognome
* PATCH "/password"            → 204 No Content | Utente autenticato | modifica della password; body: password attuale + nuova; logout ovunque
* PATCH "/avatar"              → 200 OK         | Utente autenticato | upload della foto profilo (multipart); risponde con il nuovo URL
* DELETE                       → 204 No Content | Utente autenticato | cancellazione dell'account con anonimizzazione; body: password attuale; logout ovunque
* POST "/verification"         → 204 No Content | Utente autenticato (anche con email non verificata) | verifica dell'email con il codice ricevuto
* POST "/verification/resend"  → 204 No Content | Utente autenticato (anche con email non verificata) | invio di un nuovo codice di verifica
* POST "/email"                → 204 No Content | Utente autenticato | richiesta di cambio email; body: nuova email + password attuale; invia il codice alla nuova email
* POST "/email/confirm"        → 204 No Content | Utente autenticato | conferma del cambio email con il codice; logout ovunque

#### Percorso  (`contenuti`, spec: specs/percorso.md)
@RequestMapping "/api/path"
* GET "/stages"       → 200 OK | Utente autenticato | mappa: tutte le tappe con le loro lezioni e lo stato di ognuna per l'utente loggato (COMPLETATA, SBLOCCATA, BLOCCATA)
* GET "/lessons/{id}" → 200 OK | Utente autenticato | contenuto della lezione: domande e opzioni SENZA soluzioni né spiegazioni; colonna destra degli abbinamenti mescolata; 403 se la lezione è bloccata

#### Tentativi  (`tentativi`, spec: specs/tentativi.md)
@RequestMapping "/api/attempts"
* POST ""               → 201 Created | Utente autenticato         | avvia un tentativo; body: lezioneId; chiude come FALLITO un eventuale tentativo IN_CORSO sulla stessa lezione; risponde con tentativoId
* POST "/{id}/answers"  → 200 OK      | Proprietario del tentativo | invia UNA risposta; risponde con corretta, soluzione, spiegazione, errori commessi, esito
* POST "/{id}/complete" → 200 OK      | Proprietario del tentativo | chiude il tentativo come SUPERATO e aggiorna la streak
* GET  "/{id}"          → 200 OK      | Proprietario del tentativo | resoconto: risposte date, soluzioni, spiegazioni, esito

#### Admin – Utenti  (`utente`, spec: specs/admin.md)
@RequestMapping "/api/admin"
* GET   "/users"           → 200 OK         | Admin, Superadmin | lista paginata degli utenti attivi, con ricerca opzionale `q` su nome, cognome ed email
* PATCH "/users/{id}/role" → 204 No Content | Admin, Superadmin | cambio ruolo secondo la tabella dei permessi; logout ovunque per l'utente modificato

#### Admin – Contenuti  (`contenuti`, spec: specs/admin.md)
@RequestMapping "/api/admin"
* GET    "/stages"                       → 200 OK         | Admin, Superadmin | tutte le tappe con le loro lezioni (vista di gestione)
* POST   "/stages"                       → 201 Created    | Admin, Superadmin | creazione di una tappa (in fondo all'ordine)
* PATCH  "/stages/{stageId}"             → 200 OK         | Admin, Superadmin | modifica di una tappa (titolo, descrizione, ordine)
* DELETE "/stages/{stageId}"             → 204 No Content | Admin, Superadmin | soft delete della tappa e, a cascata, di lezioni e domande
* GET    "/lessons/{lessonId}"           → 200 OK         | Admin, Superadmin | dettaglio di una lezione con domande, opzioni E soluzioni
* POST   "/stages/{stageId}/lessons"     → 201 Created    | Admin, Superadmin | creazione di una lezione nella tappa indicata (in fondo all'ordine)
* PATCH  "/lessons/{lessonId}"           → 200 OK         | Admin, Superadmin | modifica di una lezione (titolo, descrizione, ordine)
* DELETE "/lessons/{lessonId}"           → 204 No Content | Admin, Superadmin | soft delete della lezione e, a cascata, delle domande
* POST   "/lessons/{lessonId}/questions" → 201 Created    | Admin, Superadmin | creazione di una domanda con opzioni o coppie (in fondo all'ordine)
* PATCH  "/questions/{questionId}"       → 200 OK         | Admin, Superadmin | modifica dei soli testi di una domanda e delle sue opzioni/elementi
* DELETE "/questions/{questionId}"       → 204 No Content | Admin, Superadmin | soft delete di una domanda
