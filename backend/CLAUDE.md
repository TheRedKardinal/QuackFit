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
- Ricerca per id: `repository.findById(id).orElseThrow(...)` con l'eccezione del codice `NON_TROVATO`.
- Operazioni che modificano più tabelle: `@Transactional` sul metodo del service.

### Password
- Validazione: almeno 8 caratteri, almeno una maiuscola, un numero e un carattere speciale,
  e **massimo 72 byte** (limite di BCrypt).
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
  | `EMAIL_GIA_USATA`, `TENTATIVO_CHIUSO` | 409 |
  | `ATTENDI_REINVIO`, `TROPPE_RICHIESTE` | 429 |
  | `ERRORE_INTERNO` | 500 |
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
  - `/api/admin/**` → solo `ADMIN` e `SUPERADMIN`
  - tutto il resto → utente autenticato **con email verificata**; se non è verificata → 403 `EMAIL_NON_VERIFICATA`,
    tranne `GET /api/me`, `POST /api/me/verification` e `POST /api/me/verification/resend`

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
- Header di sicurezza attivi tramite Spring Security: `Content-Security-Policy`, `X-Content-Type-Options`,
  `X-Frame-Options`.
- CORS: consentita solo l'origine del frontend (da configurazione), mai `*`.

## Soft delete
- `Tappa`, `Lezione`, `Domanda` hanno `deletedAt` e usano `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")`.
- **Su `Utente` NON si usa `@SQLRestriction`**: i controlli su `deletedAt` si fanno esplicitamente
  (login, lista utenti admin), così lo storico dei tentativi resta leggibile.

## Configurazione
- `application.properties` importa le variabili da `env.properties` (`spring.config.import`).
- `env.properties` è **escluso da Git**; nel repository c'è `env.properties.example` con le chiavi vuote.
- Variabili: database, segreto e durata JWT, Cloudinary, mail (SMTP / Brevo), email e password del superadmin.
- Schema del database: `spring.jpa.hibernate.ddl-auto=update`.
- Profili:
  - default (locale): PostgreSQL locale; email via SMTP Gmail oppure codici scritti nel log
  - `test`: H2 in memoria, usato dai test di integrazione
  - `prod`: deploy online; email tramite **API HTTP di Brevo**

## Seeder
- Un `CommandLineRunner` nel package `seed`:
  - crea il **superadmin** se non esiste, con credenziali lette da `env.properties`
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

## Comandi
Il progetto gira su Windows. Usa il Maven Wrapper:
- avvio: `mvnw.cmd spring-boot:run`
- test: `mvnw.cmd test`
