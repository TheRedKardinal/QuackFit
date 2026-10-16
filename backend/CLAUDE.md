# QuackFit – Backend

Regole specifiche del backend. Valgono anche tutte le regole comuni di `../CLAUDE.md`.

## Documentazione
- `docs/progettazione.md` → entità, enum, relazioni, elenco degli endpoint
- `docs/specs/*.md` → una spec per feature (regole, casi limite, status code, codici di errore)

Prima di implementare una task, leggi la spec della feature. Se modifichi un comportamento, aggiorna la spec
nella stessa task. Ciò che non è scritto lì va chiesto prima.

## Stack
| Cosa | Scelta |
| --- | --- |
| Linguaggio | Java 25 (Temurin) |
| Framework | Spring Boot 4.1.1 (Web MVC) |
| Build | Maven (con Maven Wrapper) |
| Persistenza | Spring Data JPA + PostgreSQL |
| Sicurezza | Spring Security + JWT (libreria jjwt), BCrypt |
| Validazione | Jakarta Bean Validation |
| Email | Spring Mail + template Thymeleaf; online API HTTP di Brevo |
| Immagini | Cloudinary |
| Utilità | Lombok |
| Test | JUnit 5 + Mockito (unit), MockMvc (integrazione), H2 in memoria |

- Usa API e pattern compatibili con **Spring Boot 4** (Spring Security 7, Hibernate 7, Jakarta EE).
  Niente soluzioni da versioni vecchie (es. `WebSecurityConfigurerAdapter`, `javax.*`).
- **Non aggiungere dipendenze** al `pom.xml` senza chiedere.

## Struttura dei package (per funzionalità)
Package base: `it.quackfit`. Ogni funzionalità ha il suo package con **tutto** ciò che la riguarda:
entità, enum, repository, service, controller, DTO.

```
it.quackfit
├── auth        registrazione, login, verifica email, password dimenticata, codici (CodiceVerifica, TipoCodice)
├── utente      Utente, Ruolo, Streak, profilo (/api/me), cambio email, gestione utenti admin
├── contenuti   Tappa, Lezione, Domanda, Opzione, ElementoAbbinamento, TipoDomanda, Lato,
│               mappa e lezioni (/api/path), gestione contenuti admin
├── tentativi   TentativoLezione, EsitoTentativo, RispostaOpzione, RispostaAbbinamento (/api/attempts)
├── mail        invio email (SMTP, Brevo, log) e template Thymeleaf
├── security    filtro JWT, configurazione di Spring Security, gestione del token
├── common      ErroreResponse, CodiceErrore, ApplicazioneException, ExceptionsHandler, ClockConfig
├── config      configurazioni generali (CORS, Cloudinary, …)
└── seed        CommandLineRunner (superadmin e contenuti iniziali)
```

- Gli **enum** stanno nel package della loro funzionalità (es. `Ruolo` in `utente`, `TipoDomanda` in `contenuti`).
- Ogni package corrisponde alla spec omonima in `docs/specs/` (es. `tentativi` ↔ `tentativi.md`).
- Una classe di una funzionalità può usare classi di un'altra (es. `tentativi` usa `Lezione`),
  ma la logica di una funzionalità sta nel suo package.

## Convenzioni di codice

### Nomi
- Classi, campi e metodi in **italiano**: `Tappa`, `TentativoLezione`, `ultimaAttivita`, `deletedAt`.
- Campi Java in camelCase; colonne del database in snake_case.
- Path degli endpoint in **inglese**, al plurale, come in `docs/progettazione.md`.
- Nomi delle classi per ruolo: `TappaController`, `TappaService`, `TappaRepository`.

### Lombok
- Sulle entità: `@Getter`, `@Setter`, `@NoArgsConstructor`.
- Costruttori con parametri **scritti a mano**.
- **Vietati**: `@AllArgsConstructor`, `@Data` sulle entità, `@Builder`.

### Entità
- Id di tipo `UUID` generato automaticamente (`GenerationType.UUID`).
- Date e orari: `Instant`. Unica eccezione: `Streak.ultimaAttivita` è `LocalDate`.
- Enum salvati come stringa (`@Enumerated(EnumType.STRING)`).
- Relazioni `@ManyToOne` sempre **`fetch = FetchType.LAZY`**.
  Conseguenza: le entità si leggono e si convertono in DTO **dentro un metodo `@Transactional` del service**,
  mai nel controller.

### DTO
- Tutti i DTO sono **record**, nel package della loro funzionalità.
- Nomi: `<Cosa>Request` per l'input, `<Cosa>Response` per l'output.
- **Conversione entità → DTO**: metodo statico nel record chiamato **`da`**, invocato dal service:
  ```java
  public record MeResponse(UUID id, String nome, ...) {
      public static MeResponse da(Utente utente, int streakAttuale, ...) { return new MeResponse(...); }
  }
  ```
- **Pulizia dell'input nel costruttore compatto** dei record di richiesta: `strip()` sui testi,
  email in minuscolo. La password non si tocca.
- Validazioni sui record di richiesta; i controller usano `@Valid`.
- **I controller non restituiscono mai entità**, solo DTO.
- Le risposte destinate all'utente non devono **mai** contenere soluzioni prima della risposta:
  `corretta`, `abbinamentoCorretto`, `spiegazione` (vedi `docs/specs/percorso.md`).

### Controller e service
- I controller ricevono la richiesta, validano, chiamano il service e restituiscono il DTO con lo status code
  indicato in `docs/progettazione.md`. Nessuna logica di business nei controller.
- L'utente loggato si ottiene con `@AuthenticationPrincipal`, mai da un id passato nell'URL o nel body.
  Il principal è l'entità `Utente` caricata dal filtro JWT (autorità `ROLE_<ruolo>`; `Utente` non implementa `UserDetails`).
  È "staccato" dal database: **per leggere si usa il principal; per scrivere il service ricarica l'utente per id dentro
  il metodo `@Transactional` e modifica quello**, così non si riscrivono campi cambiati nel frattempo (es. contatori dei login).
- Ricerca per id: `repository.findById(id).orElseThrow(...)` con l'eccezione del codice `NON_TROVATO`.
- Operazioni che modificano più tabelle: `@Transactional` sul metodo del service.

### Password
- Validazione: almeno 8 caratteri, almeno una maiuscola, un numero e un carattere speciale,
  e **massimo 72 byte** (limite di BCrypt).
- Si usa l'annotazione **`@PasswordValida`** (`common`), la stessa per registrazione, reset e cambio password:
  - maiuscola = qualunque lettera maiuscola (anche accentata, es. `À`); numero = `0-9`;
    carattere speciale = qualunque carattere che non è lettera, cifra o spazio
  - i 72 byte si contano in **UTF-8** (`@Size` conta i caratteri: una lettera accentata vale 2 byte)
  - un solo messaggio: *"La password deve avere almeno 8 caratteri, una maiuscola, un numero e un carattere speciale
    (massimo 72 byte)"*; la password vuota la segnala `@NotBlank` (*"La password è obbligatoria"*)
- Password e codici di verifica si salvano solo come **hash BCrypt**.

### Tempo
- Non usare mai `Instant.now()` o `LocalDate.now()` direttamente: inietta il bean **`Clock`** (`common/ClockConfig`)
  e usa `Instant.now(clock)` / `LocalDate.now(clock)`. Nei test si sostituisce con un orologio fisso.
- Il fuso orario per "oggi" e "ieri" (streak) è **`Europe/Rome`**, definito in un'unica costante.

## Errori
Gestione centralizzata in `common/ExceptionsHandler` (`@RestControllerAdvice`). Formato di **ogni** errore:

```json
{
  "status": 400,
  "codice": "VALIDAZIONE",
  "errore": "Bad Request",
  "messaggio": "Uno o più campi non sono validi",
  "campi": { "email": "Formato email non valido" },
  "timestamp": "2026-10-14T12:00:00Z"
}
```
- `codice`: stringa stabile in MAIUSCOLO; il frontend decide in base a questo, mai al testo del messaggio.
- `campi`: valorizzato solo per errori di validazione, altrimenti vuoto.
- Mai stack trace o messaggi tecnici nella risposta. Errori imprevisti → 500 con messaggio generico.
- I codici stanno nell'enum `common/CodiceErrore`, ognuno con il suo status HTTP.
  Il service lancia `ApplicazioneException(CodiceErrore, messaggio)`.
- Codici iniziali:
  | Codice | Status |
  | --- | --- |
  | `VALIDAZIONE` | 400 |
  | `CODICE_NON_VALIDO`, `CODICE_SCADUTO`, `TROPPI_TENTATIVI` | 400 |
  | `PASSWORD_ATTUALE_ERRATA` | 400 |
  | `FILE_NON_VALIDO` | 400 |
  | `NON_AUTENTICATO`, `CREDENZIALI_NON_VALIDE` | 401 |
  | `ACCESSO_NEGATO`, `EMAIL_NON_VERIFICATA`, `LEZIONE_BLOCCATA` | 403 |
  | `NON_TROVATO` | 404 |
  | `METODO_NON_CONSENTITO` | 405 |
  | `EMAIL_GIA_USATA`, `TENTATIVO_CHIUSO` | 409 |
  | `ATTENDI_REINVIO`, `TROPPE_RICHIESTE` | 429 |
  | `ERRORE_INTERNO` | 500 |
- Errori generati da Spring prima dei service:
  | Caso | Risposta |
  | --- | --- |
  | Body JSON malformato o con tipi sbagliati | 400 `VALIDAZIONE`, `campi` vuoto |
  | Parametro dell'URL del tipo sbagliato (es. id non UUID) | 400 `VALIDAZIONE`, `campi` con il nome del parametro |
  | Endpoint inesistente | 404 `NON_TROVATO` |
  | Metodo HTTP non previsto | 405 `METODO_NON_CONSENTITO` |
- Le spec indicano quale codice usare in ogni caso. **Un codice nuovo si aggiunge solo dopo averlo chiesto**
  e va riportato nella spec.

## Sicurezza

### Autenticazione e accessi
- Sessioni **stateless** con JWT (durata 24 ore), inviato dal frontend nell'header `Authorization: Bearer ...`.
  Il token contiene id, ruolo e data di emissione.
- A ogni richiesta il filtro JWT **carica l'utente dal database** e rifiuta il token (401) se:
  - l'utente non esiste o è cancellato (`deletedAt` valorizzato)
  - il token è stato emesso **prima di `tokenValidiDal`** dell'utente
- `tokenValidiDal` si aggiorna ("logout ovunque") a: cambio password, reset password, cambio ruolo,
  cambio email, cancellazione account.
- Regole di accesso:
  - `/api/auth/**` → pubblico
  - `/api/admin/**` → solo `ADMIN` e `SUPERADMIN`, **con email verificata**. Si controlla prima il ruolo (non admin → 403
    `ACCESSO_NEGATO`), poi l'email (admin non verificato → 403 `EMAIL_NON_VERIFICATA`)
  - tutto il resto → utente autenticato **con email verificata**; se non è verificata → 403 `EMAIL_NON_VERIFICATA`,
    tranne `GET /api/me`, `POST /api/me/verification` e `POST /api/me/verification/resend`
- Nessun utente in memoria: `UserDetailsServiceAutoConfiguration` è esclusa in `application.properties`
  (`spring.autoconfigure.exclude`). Gli utenti sono solo quelli del database, caricati dal filtro JWT; senza l'esclusione
  Spring Boot creerebbe un utente `user` e scriverebbe la sua password generata nel log.

### Login
- Messaggio unico per ogni login fallito, anche ad account bloccato (401 `CREDENZIALI_NON_VALIDE`):
  *"Email o password non corretti, oppure troppi tentativi: riprova tra qualche minuto."*
- Blocco dell'account per 15 minuti (`bloccatoFino`) se:
  - 3 login falliti entro 10 secondi dal primo (comportamento da bot), oppure
  - 5 login falliti in totale
- Un login riuscito azzera il contatore; dopo 15 minuti senza errori il conteggio riparte.
- Password dimenticata: risposta sempre generica, non rivelare se l'email è registrata.

### Codici email
- 6 cifre, salvati come hash BCrypt, validi 15 minuti, massimo 5 tentativi.
- Un nuovo codice dello stesso tipo **invalida i precedenti** (vale solo l'ultimo).
- Tra un invio e l'altro dello stesso tipo servono **60 secondi**, altrimenti 429 `ATTENDI_REINVIO`.

### Limite per indirizzo IP
- Filtro che limita le richieste su `/api/auth/**` a **20 al minuto per IP**; oltre → 429 `TROPPE_RICHIESTE`,
  senza eseguire la richiesta.
- I contatori stanno **solo in memoria** (`ConcurrentHashMap`), mai nel database (l'IP è un dato personale).
  La mappa va ripulita periodicamente dagli IP vecchi.
- Dietro il proxy di Render l'IP reale va letto da `X-Forwarded-For` (configurazione di Spring per gli header inoltrati).
- Nessuna libreria esterna.
- **Finestra fissa**: per ogni IP un contatore e l'istante della prima richiesta; dopo 20 richieste si risponde 429
  finché non scadono i 60 secondi, poi il conteggio riparte. A cavallo di due finestre possono passare fino a
  40 richieste in poco tempo: accettato, perché login e codici sono protetti anche dal blocco dell'account e dai
  5 tentativi sui codici.
- **Pulizia**: un metodo `@Scheduled` ogni 5 minuti toglie dalla mappa gli IP con la finestra scaduta
  (`@EnableScheduling` in `config`).
- **IP reale**: `server.forward-headers-strategy=native` **solo nel profilo `prod`**. La `RemoteIpValve` di Tomcat legge
  `X-Forwarded-For` da destra e salta i proxy interni (IP privati), quindi un valore inventato dal client non cambia
  l'IP contato. Non si usa `framework` (`ForwardedHeaderFilter`): prende il primo valore, scritto dal client.
  Se i proxy di Render non fossero riconosciuti come interni, tutti gli utenti avrebbero lo stesso IP e un solo
  contatore: va verificato al deploy (task 49). In locale si usa l'IP diretto della connessione.

### SQL injection
- Solo query **parametrizzate**: metodi di Spring Data JPA o `@Query` con parametri (`:email`).
- **Mai** concatenare stringhe o input dell'utente dentro una query JPQL o SQL.

### CSRF
- Il token viaggia nell'header `Authorization`, non in un cookie: il browser non lo invia da solo,
  quindi il CSRF non è applicabile e la protezione CSRF di Spring Security resta disattivata.
- Non introdurre autenticazione basata su cookie senza chiedere: cambierebbe questa scelta.

### XSS
- Le API restituiscono solo JSON, mai HTML costruito con input dell'utente.
- Template email Thymeleaf: solo `th:text` (fa l'escape), **mai `th:utext`**.
- Header di sicurezza attivi tramite Spring Security:
  - `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` (le API restituiscono solo JSON:
    non serve consentire nessuna risorsa)
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
- CORS: consentita solo l'origine del frontend (da configurazione), mai `*`.

## Regole ricorrenti
Regole di default per le task che salvano tentativi, contatori o codici (login, codici, reset, cambio password ed email).
Si applicano senza chiedere e si indicano nel piano della task; si chiede solo per un caso che non rientra in nessuna.

1. **Salvare e poi rispondere con un errore**: se un metodo deve salvare dati (contatori, tentativi, codici) e poi
   lanciare l'eccezione applicativa, usa `noRollbackFor = ApplicazioneException.class` (più `REQUIRES_NEW` se il metodo
   può essere chiamato dentro la transazione di un altro service, vedi `auth.md`, Codici email). Nel test di integrazione
   **rileggi il dato dal database** dopo l'errore.
2. **Contatori**: la lettura dell'utente di cui si aggiornano i contatori usa un lock pessimistico
   (`@Lock(LockModeType.PESSIMISTIC_WRITE)`), così richieste in parallelo non perdono aggiornamenti.
3. **Non rivelare se un'email è registrata**, né nel messaggio né nei tempi: dove l'utente non esiste (o è cancellato o
   bloccato) si confronta comunque la password con un **hash fittizio** calcolato all'avvio con lo stesso `PasswordEncoder`.
4. **Campi obbligatori mancanti o vuoti** → 400 `VALIDAZIONE` con `@NotBlank` e messaggio *"Il/L' «campo» è
   obbligatorio/a"* (es. *"L'email è obbligatoria"*, *"La password è obbligatoria"*).
5. **Password oltre i 72 byte**: mai un 500 (BCrypt lancia un'eccezione oltre i 72 byte). In input si valida con
   `@PasswordValida`; dove si confronta una password esistente conta come sbagliata, senza chiamare BCrypt.
   La soglia è la costante di `PasswordValidaValidator`, non va riscritta.
6. **Lavoro asincrono solo per la password dimenticata**: lì tutto il lavoro sul codice (ricerca dell'utente, controllo
   dei 60 secondi, hash, salvataggio, invio) è `@Async`, così la durata della risposta non rivela se l'email esiste
   (vedi `auth.md`, Password dimenticata, e `config/AsyncConfig`). Registrazione, verifica email e reinvio restano
   sincroni. Nel profilo `test` l'executor è sincrono.

## Soft delete
- `Tappa`, `Lezione`, `Domanda` hanno `deletedAt` e usano `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")`.
- **Su `Utente` NON si usa `@SQLRestriction`**: i controlli su `deletedAt` si fanno esplicitamente
  (login, lista utenti admin), così lo storico dei tentativi resta leggibile.

## Configurazione
- `application.properties` importa le variabili da `env.properties` (`spring.config.import`).
- `env.properties` è **escluso da Git**; nel repository c'è `env.properties.example` con le chiavi vuote.
- Variabili: database, segreto e durata JWT, Cloudinary, mail (SMTP / Brevo), email e password del superadmin.
- Chiavi della sicurezza:
  | Chiave | Obbligatoria | Default | Uso |
  | --- | --- | --- | --- |
  | `JWT_SECRET` | sì, almeno 32 caratteri | — | firma HMAC-SHA256 dei token; mai in un file tracciato |
  | `JWT_DURATA` | no | `24h` | durata del token |
  | `FRONTEND_URL` | no | `http://localhost:5173` | unica origine ammessa dal CORS |
- Chiavi del superadmin (vedi Seeder):
  | Chiave | Obbligatoria | Default | Uso |
  | --- | --- | --- | --- |
  | `SUPERADMIN_EMAIL` | sì, finché nel database non c'è un SUPERADMIN | — | email del superadmin creato all'avvio |
  | `SUPERADMIN_PASSWORD` | sì, finché nel database non c'è un SUPERADMIN | — | password, con le regole di `@PasswordValida`; mai in un file tracciato |
- Chiavi delle email:
  | Chiave | Obbligatoria | Default | Uso |
  | --- | --- | --- | --- |
  | `MAIL_MODALITA` | no | `log` | `log` (codici nel log, mai in `prod`), `smtp` (Gmail, solo locale), `brevo`; in `prod` è fissata a `brevo` |
  | `MAIL_MITTENTE` | con `smtp` e `brevo` | — | indirizzo del mittente; con Brevo deve essere un mittente verificato nell'account |
  | `MAIL_NOME_MITTENTE` | no | `QuackFit` | nome del mittente |
  | `MAIL_USERNAME`, `MAIL_PASSWORD` | con `smtp` | — | account Gmail e password per app |
  | `BREVO_API_KEY` | con `brevo` | — | chiave API di Brevo; mai in un file tracciato |
- Regole delle email (package `mail`):
  - `MailService` ha un metodo per ogni email (verifica, reset password, cambio email); le implementazioni si scelgono
    con `MAIL_MODALITA`. Quella di log ha anche `@Profile("!prod")`: in produzione non esiste.
  - Invio **sincrono** (per la password dimenticata è asincrono tutto il lavoro del chiamante, vedi Regole ricorrenti).
    Il servizio **non lancia eccezioni**: se la consegna fallisce registra l'errore e prosegue
    (registrazione, reinvio e password dimenticata non devono fallire per colpa dell'email, vedi `auth.md`).
  - Nei log di errore **mai il codice e mai l'indirizzo completo** (solo mascherato, es. `m***@example.com`); di Brevo
    si registra solo lo stato HTTP, mai il corpo della richiesta né la chiave API. Il codice in chiaro compare solo
    nell'implementazione di log.
  - Timeout di 10 secondi sia per Brevo (connessione e risposta) sia per l'SMTP (connessione, lettura, scrittura).
- Schema del database: `spring.jpa.hibernate.ddl-auto=update`.
- `spring.jpa.open-in-view=false`: le relazioni LAZY si leggono solo dentro i service `@Transactional`.
- Profili:
  - default (locale): PostgreSQL locale; email via SMTP Gmail oppure codici scritti nel log
  - `test`: H2 in memoria, usato dai test di integrazione
  - `prod`: deploy online; email tramite **API HTTP di Brevo**

## Seeder
- Un `CommandLineRunner` nel package `seed`:
  - crea il **superadmin** se non esiste, con credenziali lette da `env.properties`
    (`SuperadminSeeder`):
    - se esiste già un SUPERADMIN non fa nulla, anche se `SUPERADMIN_EMAIL` è cambiata (il superadmin non può
      cancellarsi, vedi `me.md`)
    - altrimenti lo crea con nome *Super*, cognome *Admin*, email pulita come in registrazione (`strip()`, minuscolo)
      e già verificata, password con hash BCrypt e la sua `Streak` a zero
    - **l'app non parte** (eccezione all'avvio con un messaggio chiaro) se, senza un SUPERADMIN, le chiavi mancano o
      sono vuote, l'email non ha un punto nel dominio, la password non rispetta `@PasswordValida`, oppure l'email è già
      usata da un altro utente (che **non** viene promosso)
    - non gira nel profilo `test` (`@Profile("!test")`): il superadmin con la sua streak resterebbe nel database
      condiviso dai test; il suo test lo costruisce e lo esegue a mano
  - inserisce i **contenuti iniziali** (tappe, lezioni, domande) **solo se non esiste nessuna tappa**
- I contenuti iniziali sono scritti in Java, generati con Claude e **revisionati dall'utente** prima del commit.

## Test
- Ogni feature ha i suoi test, scritti nella stessa task:
  - **unit test dei service** (JUnit 5 + Mockito) per tutte le regole delle spec
    (streak, tentativi ed errori, ruoli, validazioni delle domande, codici email, …)
  - **test di integrazione dei controller** (MockMvc + profilo `test` con H2) per i flussi principali e i permessi
- Per le regole legate alle date si usa un `Clock` fisso.
- I test devono passare prima di proporre un commit.
- Solo strumenti visti al corso o simili; niente librerie di test aggiuntive senza chiedere.
- **Database condiviso tra i test**: tutti i test di integrazione usano lo stesso H2 in memoria (anche con contesti
  Spring diversi). Ogni classe di test che crea dati li cancella **prima e dopo ogni test** (`@BeforeEach` + `@AfterEach`),
  partendo dalle tabelle con la chiave esterna verso l'utente (codici, streak, tentativi…) e finendo con gli utenti.
  Un nuovo endpoint che crea dati può rompere la pulizia di un test più vecchio che lo chiama: in quel caso si sistema
  la pulizia di quel test nella stessa task (la suite lo segnala con un errore di chiave esterna).

## Comandi
Il progetto gira su Windows. Usa il Maven Wrapper:
- avvio: `mvnw.cmd spring-boot:run`
- test: `mvnw.cmd test`
