# Spec: Auth

## Obiettivo
Registrazione, login, verifica dell'email e recupero della password, con protezioni contro
tentativi ripetuti e senza rivelare quali email sono registrate.

Package: `auth`. Endpoint: vedi `progettazione.md` → Auth, e → Me per la verifica email.

---

## Codici email (regole comuni)
Valgono per tutti i tipi di codice: VERIFICA_EMAIL, RESET_PASSWORD, CAMBIO_EMAIL.

- Codice numerico a **6 cifre**, generato con `SecureRandom`.
- Salvato in `CodiceVerifica` solo come **hash BCrypt**; il codice in chiaro esiste solo nell'email.
- Valido **15 minuti** (`scadenza`).
- **Massimo 5 tentativi** sbagliati (`tentativi_falliti`).
- Quando si genera un nuovo codice dello stesso tipo per lo stesso utente, i precedenti ancora validi
  vengono **invalidati** (`usato_at = adesso`): vale solo l'ultimo.
- Tra due invii dello stesso tipo per lo stesso utente devono passare **60 secondi**.
- Email inviate con template Thymeleaf (solo `th:text`).
- In locale il codice può essere scritto nel log; **mai** nel profilo `prod`.
- Chi genera e invia: **`CodiceService`** (`generaEInvia(utente, tipo, nuovaEmail)`) genera il codice, salva l'hash e
  invia l'email giusta per il tipo (per CAMBIO_EMAIL alla **nuova** email). Il codice in chiaro non esce da quel metodo.
- Limite dei 60 secondi: `generaEInvia` risponde 429 `ATTENDI_REINVIO`. Dove la spec vuole il silenzio
  (password dimenticata) il chiamante usa prima `reinvioConsentito(utente, tipo)` e, se è troppo presto, non fa nulla.
  I 60 secondi si contano dalla creazione dell'ultimo codice dello stesso tipo, usato o no.
- Messaggi degli errori:
  | Codice | Messaggio |
  | --- | --- |
  | `CODICE_NON_VALIDO` | *"Codice non valido"* |
  | `CODICE_SCADUTO` | *"Codice scaduto: richiedine uno nuovo"* |
  | `TROPPI_TENTATIVI` | *"Troppi tentativi: richiedi un nuovo codice"* |
  | `ATTENDI_REINVIO` | *"Attendi un minuto prima di richiedere un nuovo codice"* |
- La verifica gira in una **transazione propria** (`REQUIRES_NEW`) che **non fa rollback sugli errori dei codici**
  (`noRollbackFor = ApplicazioneException`), così il `+1` ai tentativi resta salvato anche quando si risponde con un
  errore e anche se il chiamante annulla la sua transazione. Serve solo sul metodo `verifica`, che va chiamato da un altro
  bean (proxy di Spring); i chiamanti non devono fare nulla. Dentro quella transazione si carica, controlla, conta e marca "usato" il codice;
  al chiamante torna solo l'esito (per CAMBIO_EMAIL la nuova email), mai l'entità. Conseguenza: un codice giusto risulta **usato** anche se l'operazione del chiamante fallisce
  dopo (per esempio con il 409 `EMAIL_GIA_USATA` della conferma del cambio email): l'utente deve chiederne uno nuovo.

### Verifica di un codice
Dato l'utente e il tipo, si prende l'ultimo codice con `usato_at = null`:
1. nessun codice valido → 400 `CODICE_NON_VALIDO`
2. `scadenza` passata → 400 `CODICE_SCADUTO`
3. `tentativi_falliti` già a 5 → 400 `TROPPI_TENTATIVI`
4. codice sbagliato → `tentativi_falliti + 1`; se arriva a 5 → 400 `TROPPI_TENTATIVI`, altrimenti 400 `CODICE_NON_VALIDO`
5. codice giusto → `usato_at = adesso` e si prosegue con l'operazione

**Confronto fittizio**: nei casi 1-3 il codice ricevuto non si confronta con nessun hash; si fa comunque un confronto
con un hash calcolato all'avvio con lo stesso `PasswordEncoder` (`CodiceService.confrontoFittizio`), così ogni verifica
dura come un confronto vero. Vale per **tutti i tipi di codice**, quindi anche per la verifica dell'email (che però
mantiene gli errori distinti). Lo stesso confronto si usa nella reimpostazione della password quando l'email non è
registrata.

---

## Registrazione — POST /api/auth/register
Body: `nome`, `cognome`, `email`, `password`.

- Pulizia nel record: `strip()` su nome, cognome ed email; email in minuscolo.
- Validazioni (400 `VALIDAZIONE`, con i `campi` sbagliati):
  - nome, cognome, email, password obbligatori e non vuoti
  - email in formato valido
  - password: almeno 8 caratteri, una maiuscola, un numero, un carattere speciale, massimo 72 byte
  - lunghezze massime: nome e cognome **50** caratteri, email **254** (limite standard degli indirizzi email)
  - formato email: `@Email` di Jakarta **più** un dominio con almeno un punto (`mario@localhost` non è valido)
  - un campo vuoto ha un solo messaggio, quello di `@NotBlank`: le altre regole (formato email, password) lo lasciano passare
  - password: annotazione `@PasswordValida` (vedi `backend/CLAUDE.md`, Password)
- Messaggi dei campi:
  | Caso | Messaggio |
  | --- | --- |
  | nome vuoto | *"Il nome è obbligatorio"* |
  | cognome vuoto | *"Il cognome è obbligatorio"* |
  | email vuota | *"L'email è obbligatoria"* |
  | email non valida | *"Formato email non valido"* |
  | password vuota | *"La password è obbligatoria"* |
  | password non valida | *"La password deve avere almeno 8 caratteri, una maiuscola, un numero e un carattere speciale (massimo 72 byte)"* |
  | nome o cognome troppo lungo | *"Massimo 50 caratteri"* |
  | email troppo lunga | *"Massimo 254 caratteri"* |
- Email già usata da un utente attivo (`deleted_at = null`) → 409 `EMAIL_GIA_USATA`, messaggio:
  *"Email già registrata. Se è tua, accedi oppure usa 'Password dimenticata'."*
  (le email degli account anonimizzati non contano: sono già state sostituite)
- Due registrazioni simultanee con la stessa email: il controllo "esiste già?" e il salvataggio non sono atomici.
  L'utente si salva con `saveAndFlush` dentro un `try/catch` su `DataIntegrityViolationException` (l'unico vincolo
  `UNIQUE` di Utente è l'email): la seconda riceve lo stesso 409 `EMAIL_GIA_USATA`, non un 500.
- Crea l'Utente: ruolo UTENTE, `email_verificata = false`, `token_validi_dal = adesso`, contatori login a zero.
- Crea la sua Streak: valore 0, record 0, `ultima_attivita = null`.
- Genera e invia un codice VERIFICA_EMAIL.
- Se l'invio dell'email fallisce, la registrazione resta valida: l'utente può chiedere un nuovo codice.
- Risponde **201** con il solo token JWT.

## Login — POST /api/auth/login
Body: `email`, `password`.

- Pulizia nel record: email con `strip()` e in minuscolo.
- Ogni fallimento risponde **401 `CREDENZIALI_NON_VALIDE`** con un unico messaggio:
  *"Email o password non corretti, oppure troppi tentativi: riprova tra qualche minuto."*
- Casi di fallimento:
  - email inesistente o utente cancellato
  - account bloccato (`bloccato_fino` nel futuro): non si controlla nemmeno la password
  - password sbagliata (vedi contatori sotto)
- Login riuscito: `login_falliti = 0`, `primo_login_fallito_at = null`, `bloccato_fino = null`;
  risponde **200** con il solo token JWT.
- Il login è consentito anche con email non verificata: il token serve per verificarla
  (gli altri endpoint rispondono 403 `EMAIL_NON_VERIFICATA`).
- Campi vuoti: "ogni fallimento → 401" **non** vale per email o password mancanti o vuote, che rispondono
  **400 `VALIDAZIONE`** (`@NotBlank`: *"L'email è obbligatoria"*, *"La password è obbligatoria"*) senza toccare i contatori.
  Sull'email non c'è controllo di formato: un'email strana dà semplicemente 401.
- Scelte di implementazione (vedi anche `backend/CLAUDE.md`, Regole ricorrenti):
  - **Contatori salvati anche con il 401**: `login` ha `noRollbackFor = ApplicazioneException`, altrimenti il rollback
    cancellerebbe il `+1` e il blocco non scatterebbe mai. `login` è chiamato solo dal controller (nessuna transazione
    esterna), quindi non serve `REQUIRES_NEW`.
  - **Tentativi in parallelo**: l'utente si legge con un lock pessimistico (`PESSIMISTIC_WRITE`, `SELECT ... FOR UPDATE`):
    due login sullo stesso account si mettono in fila e nessun tentativo si perde.
  - **Stessi tempi di risposta**: per email inesistente, utente cancellato e account bloccato la password si confronta
    con un **hash fittizio**, calcolato all'avvio con lo stesso `PasswordEncoder` (stesso costo BCrypt), così i tempi non
    rivelano se l'email è registrata. Per l'account bloccato la password vera resta non controllata.
  - **Password oltre i 72 byte** (soglia di `@PasswordValida`): conta come password sbagliata (contatori e 401), mai un 500.
    In quel caso non si chiama BCrypt né per un utente esistente né per uno inesistente, così la durata è la stessa.

### Contatori dei login falliti
A ogni password sbagliata:
1. se `primo_login_fallito_at` è null o è passato più di 15 minuti → nuova serie:
   `login_falliti = 1`, `primo_login_fallito_at = adesso`
2. altrimenti → `login_falliti + 1`
3. blocco se:
   - `login_falliti >= 3` ed è passato al massimo **10 secondi** da `primo_login_fallito_at` (bot), oppure
   - `login_falliti >= 5`
4. al blocco: `bloccato_fino = adesso + 15 minuti`, `login_falliti = 0`, `primo_login_fallito_at = null`

## Verifica email — POST /api/me/verification
Utente autenticato, anche con email non verificata. Body: `codice`.

- Verifica del codice VERIFICA_EMAIL (vedi sopra).
- Codice giusto → `email_verificata = true`. Risponde **204**.
- Email già verificata → 204 senza modifiche.

## Nuovo codice di verifica — POST /api/me/verification/resend
Utente autenticato, anche con email non verificata.

- Email già verificata → 204 senza inviare nulla.
- Meno di 60 secondi dall'ultimo invio → 429 `ATTENDI_REINVIO`.
- Altrimenti genera e invia un nuovo codice (invalidando il precedente). Risponde **204**.

## Password dimenticata — POST /api/auth/password/forgot
Pubblico. Body: `email`.

- Risponde **sempre 200** con lo stesso messaggio:
  *"Se l'email è registrata, riceverai un codice per reimpostare la password."*
- Solo se l'utente esiste ed è attivo **e** sono passati almeno 60 secondi dall'ultimo invio:
  genera e invia un codice RESET_PASSWORD.
- Il limite dei 60 secondi qui **non** restituisce 429: rivelerebbe che l'email esiste.
- Vale anche per account con email non verificata.
- Corpo della risposta: `{ "messaggio": "..." }` (stessa chiave del formato degli errori).
- **Tutto il lavoro sul codice è asincrono** (`@Async`): ricerca dell'utente, controllo dei 60 secondi
  (`reinvioConsentito`), generazione e hash BCrypt del codice, salvataggio e invio. Il controller valida l'input e
  risponde subito con il 200, così la durata della risposta non dipende dall'esistenza dell'email (l'hash del codice
  è lento quanto l'invio).
  - Il metodo `@Async` sta in un bean diverso dal chiamante (`RecuperoPasswordService`, chiamato dal controller),
    altrimenti il proxy di Spring non scatta; la transazione è dentro il metodo asincrono.
  - Executor esplicito in `config/AsyncConfig`: 2 thread, coda di 100; a coda piena la richiesta si scarta e si logga,
    senza far fallire la risposta. L'executor non è un bean, così quello di default di Spring Boot resta com'è.
  - Chiusura ordinata: `AsyncConfig` è uno `SmartLifecycle` che all'arresto si ferma **dopo il web server** e prima
    della distruzione dei bean (database, mail): non accetta nuovi lavori e finisce quelli in coda, per al massimo
    20 secondi (Render concede 30 secondi tra SIGTERM e la chiusura forzata).
  - Gli errori del lavoro asincrono non arrivano al client: li logga un `AsyncUncaughtExceptionHandler` con la classe
    dell'errore e quella della causa originale, mai i messaggi né i parametri (email, codice).
  - Nel profilo `test` l'executor è un `SyncTaskExecutor`: il lavoro finisce prima della risposta e i test ne
    controllano subito il risultato.
  - Solo qui: registrazione, verifica email e reinvio del codice restano sincroni.

## Reimpostazione password — POST /api/auth/password/reset
Pubblico. Body: `email`, `codice`, `nuovaPassword`.

- Validazione della nuova password come in registrazione (400 `VALIDAZIONE`); email e codice solo obbligatori,
  senza controlli di formato.
- **Ogni fallimento risponde allo stesso modo**: 400 `CODICE_NON_VALIDO` con il messaggio
  *"Codice non valido o scaduto: richiedine uno nuovo"*, per email inesistente o utente cancellato, nessun codice,
  codice sbagliato, scaduto o con i tentativi esauriti. Motivo: la password dimenticata è pubblica e chiunque può far
  generare un codice per l'email di un altro; `CODICE_SCADUTO` e `TROPPI_TENTATIVI` rivelerebbero che l'email è registrata.
  - I controlli interni restano quelli della verifica (vedi sopra): il `+1` ai tentativi e il blocco del codice dopo
    5 errori funzionano come sempre, cambia solo la risposta.
  - Email inesistente o utente cancellato: confronto fittizio del codice, così non cambiano nemmeno i tempi.
  - La verifica dell'email (`/api/me/verification`) mantiene invece gli errori distinti: è autenticata.
- Verifica del codice RESET_PASSWORD (vedi sopra), con l'utente letto con un lock pessimistico (regola dei contatori).
- Codice giusto:
  - password = hash BCrypt della nuova
  - `email_verificata = true` (l'utente ha dimostrato di ricevere le email a quell'indirizzo)
  - `token_validi_dal = adesso` (logout ovunque)
  - sblocco del login: `login_falliti = 0`, `primo_login_fallito_at = null`, `bloccato_fino = null`
- Risponde **204**.

## Note
- Logout: non esiste un endpoint; il frontend cancella il token.
- Gli endpoint `/api/auth/**` sono protetti anche dal limite per IP (20 richieste al minuto, 429 `TROPPE_RICHIESTE`).
- Validità del token: il filtro JWT confronta la data di emissione del token (`iat`, in secondi) con `token_validi_dal`
  **troncato al secondo** e rifiuta il token se `iat` è precedente. Nel database `token_validi_dal` resta preciso.
  Un token emesso nello stesso secondo di `token_validi_dal` è quindi valido (necessario perché il token creato alla
  registrazione non venga rifiutato subito); la finestra di meno di un secondo dopo un "logout ovunque" è accettata.
- Token non valido (firma sbagliata, scaduto, utente cancellato, precedente a `token_validi_dal`) su `/api/auth/**`:
  viene **ignorato** e la richiesta prosegue senza autenticazione, così un vecchio token rimasto nel browser non blocca
  login e registrazione. Sulle altre rotte lo stesso token risponde 401 `NON_AUTENTICATO`.

## Test da prevedere
- Registrazione: dati validi, campi non validi, password che non rispetta la regex, email già usata.
- Login: credenziali giuste, sbagliate, email inesistente (stesso messaggio), account bloccato con password giusta.
- Contatori: 3 errori in 10 secondi → blocco; 3 errori in più di 10 secondi → nessun blocco; 5 errori → blocco;
  blocco scaduto; login riuscito che azzera il contatore.
- Codici: giusto, sbagliato, scaduto, 5 tentativi, codice vecchio invalidato da uno nuovo, reinvio prima di 60 secondi.
- Password dimenticata: email esistente, inesistente e in attesa del reinvio danno la stessa risposta; il codice viene
  creato e inviato solo quando l'email esiste.
- Reset: password cambiata, email verificata, login sbloccato, token precedenti rifiutati; tutti i fallimenti con
  status, codice e messaggio identici.
- Validità del token (con date fisse): token emesso nello stesso secondo di `token_validi_dal` accettato (anche subito
  dopo la registrazione); token emesso un secondo prima rifiutato.
- Token scaduto o non valido su `/api/auth/login`: il login funziona lo stesso; lo stesso token su una rotta protetta
  → 401 `NON_AUTENTICATO`.
