# Spec: Me (profilo dell'utente loggato)

## Obiettivo
Lettura e modifica del proprio profilo: dati, password, foto, email, cancellazione dell'account.

Package: `utente`. Endpoint: vedi `progettazione.md` → Me.
La verifica dell'email (`/verification`, `/verification/resend`) è descritta in `auth.md`.

---

## Regole comuni
- Tutti gli endpoint richiedono l'utente autenticato.
- Con email **non verificata** sono consentiti solo `GET /api/me`, `POST /api/me/verification` e
  `POST /api/me/verification/resend`; gli altri rispondono 403 `EMAIL_NON_VERIFICATA`.
- Le operazioni che richiedono la **password attuale** rispondono 400 `PASSWORD_ATTUALE_ERRATA` se è sbagliata
  (400, non 401: chi la sbaglia non viene disconnesso). Oltre i 72 byte conta come sbagliata, senza chiamare BCrypt.
- Le stesse operazioni (`PATCH /api/me/password`, `POST /api/me/email`, `DELETE /api/me`) rientrano nel **limite
  per IP** di 20 richieste al minuto (429 `TROPPE_RICHIESTE`, vedi `backend/CLAUDE.md`, Limite per IP), così un token
  rubato non permette di provare la password attuale a raffica.
- "Logout ovunque" = `token_validi_dal = adesso`: anche il token usato per la chiamata smette di funzionare,
  quindi il frontend deve cancellare il token e riportare l'utente al login.
- Codici email: regole comuni in `auth.md` (6 cifre, hash BCrypt, 15 minuti, 5 tentativi, 60 secondi tra due invii).

---

## Dati del profilo — GET /api/me
Risponde **200** con:
- `nome`, `cognome`, `email`, `ruolo`, `fotoProfilo` (URL o null)
- `emailVerificata`: serve al frontend per decidere se mostrare la schermata del codice; **non** va mostrato nel profilo
- `streak`: oggetto con `valore` e `record`
- `lezioniCompletate`: numero di lezioni attive con almeno un tentativo SUPERATO dell'utente

Nessun `id` nella risposta: il frontend non ne ha bisogno, ogni endpoint usa l'utente del token.

Funziona anche con email non verificata.

Ogni utente attivo ha la sua `Streak` (creata alla registrazione e dal seeder). Se manca è un dato incoerente:
500 `ERRORE_INTERNO` con il messaggio generico; nel log finisce l'id dell'utente (mai l'email).

### Streak calcolata in lettura
Lo streak salvato non viene modificato dalla lettura. Si calcola solo il valore da mostrare:
- `ultima_attivita` = oggi o ieri (fuso `Europe/Rome`) → `valore` salvato
- `ultima_attivita` null o più vecchia di ieri → `valore` mostrato = 0 (il `record` resta)

---

## Modifica dati — PATCH /api/me
Body: `nome`, `cognome`. Risponde **204**.

- `strip()` nel record; entrambi obbligatori, non vuoti, massimo **50 caratteri** (400 `VALIDAZIONE`), con gli stessi
  messaggi della registrazione (*"Il nome è obbligatorio"*, *"Il cognome è obbligatorio"*, *"Massimo 50 caratteri"*).
- Email, ruolo e password non si cambiano da qui. Non fa logout.

---

## Cambio password — PATCH /api/me/password
Body: `passwordAttuale`, `nuovaPassword`. Risponde **204**.

Controlli, in ordine:
1. campi mancanti o nuova password non valida (stesse regole della registrazione) → 400 `VALIDAZIONE` con i `campi`
   (*"La password attuale è obbligatoria"*, *"La nuova password è obbligatoria"*, messaggio di `@PasswordValida`).
   Vengono da `@Valid`, quindi prima di tutto il resto
2. password attuale sbagliata → 400 `PASSWORD_ATTUALE_ERRATA`
3. nuova password uguale all'attuale → 400 `VALIDAZIONE` con `campi: { nuovaPassword: "La nuova password deve essere
   diversa da quella attuale" }`. Viene **dopo** il punto 2, così non rivela se una password è quella attuale
4. ok → nuovo hash BCrypt e **logout ovunque**

---

## Foto profilo — PATCH /api/me/avatar
Multipart, campo `file`. Risponde **200** con il nuovo URL (`fotoProfilo`).

- Formati: **jpg, png, webp**; massimo **2 MB** (2 × 1024 × 1024 byte).
- Il tipo si controlla sul contenuto reale del file, dai primi byte ("magic number"): JPEG `FF D8 FF`,
  PNG `89 50 4E 47 0D 0A 1A 0A`, WEBP `RIFF` + 4 byte + `WEBP`. Estensione e content-type dichiarato non contano.
- File mancante, vuoto, troppo grande o di tipo non ammesso, richiesta non multipart → 400 `FILE_NON_VALIDO`
  (*"Immagine non valida: usa un file jpg, png o webp fino a 2 MB"*). Oltre i limiti di Spring
  (`spring.servlet.multipart`: 2 MB per il file, 3 MB per la richiesta) risponde lo stesso errore, mai un 500.
- Su Cloudinary la foto ha un nome fisso legato all'utente (`avatar/<id utente>`, `overwrite` e `invalidate`):
  una nuova foto **sostituisce** la vecchia, non c'è nulla da conservare né da cancellare. L'URL contiene la versione
  (`/v123.../`), quindi cambia a ogni foto e il browser non mostra quella vecchia dalla cache.
- Si salva in `foto_profilo` l'URL sicuro (https) restituito da Cloudinary.
- Ordine: prima l'upload, **fuori da ogni transazione**; poi una UPDATE della sola colonna `foto_profilo`.
- Se Cloudinary non risponde, rifiuta la richiesta o le chiavi mancano → 500 `ERRORE_INTERNO` con il messaggio
  generico; nel log solo il motivo (stato HTTP, tipo d'errore o nomi delle chiavi). La foto precedente resta com'è.

---

## Cambio email
Due passaggi: l'email cambia solo quando il nuovo indirizzo è confermato con il codice.

### Richiesta — POST /api/me/email
Body: `nuovaEmail`, `password`. Risponde **204**.

Pulizia nel record: `nuovaEmail` con `strip()` e in minuscolo; la password non si tocca.

Controlli, in ordine (come nel cambio password, prima `@Valid`):
1. campi mancanti o `nuovaEmail` non valida → 400 `VALIDAZIONE` con i `campi`, stesse regole e messaggi della
   registrazione (obbligatoria, formato con il punto nel dominio, massimo 254 caratteri); *"La password è obbligatoria"*
2. password attuale sbagliata → 400 `PASSWORD_ATTUALE_ERRATA`
3. `nuovaEmail` uguale a quella attuale (confronto dopo la pulizia) → 400 `VALIDAZIONE` con
   `campi: { nuovaEmail: "La nuova email deve essere diversa da quella attuale" }`
4. `nuovaEmail` già usata da un utente attivo → 409 `EMAIL_GIA_USATA` (*"Questa email è già usata da un altro account"*)
5. meno di 60 secondi dall'ultimo invio di un codice CAMBIO_EMAIL → 429 `ATTENDI_REINVIO`

I punti 3-5 vengono dopo la password: senza conoscerla, un token rubato non rivela quali email sono registrate.

Se ok: genera un codice CAMBIO_EMAIL con `nuova_email` valorizzata (invalidando i precedenti dello stesso tipo)
e lo invia alla **nuova** email. L'email dell'utente resta quella vecchia.

### Conferma — POST /api/me/email/confirm
Body: `codice`. Risponde **204**.

1. `codice` mancante → 400 `VALIDAZIONE` (*"Il codice è obbligatorio"*); `strip()` nel record, nessun controllo di formato
2. verifica del codice CAMBIO_EMAIL (ordine dei controlli in `auth.md`)
3. se nel frattempo la nuova email è stata presa da un altro utente → 409 `EMAIL_GIA_USATA`. Vale anche per due
   conferme in parallelo: la UPDATE parte subito (`saveAndFlush`) e la violazione dell'UNIQUE sull'email diventa un 409,
   come nella registrazione. Il codice resta comunque usato (vedi `auth.md`): ne serve uno nuovo
4. ok → `email` = `nuova_email`, `email_verificata = true`, **logout ovunque**

---

## Cancellazione account — DELETE /api/me
Body: `password`. Risponde **204**.

- Password sbagliata → 400 `PASSWORD_ATTUALE_ERRATA`.
- Il SUPERADMIN non può cancellare il proprio account → 403 `ACCESSO_NEGATO`.
- Anonimizzazione (non si elimina la riga Utente, perché i tentativi restano):
  - `nome` = "Utente", `cognome` = "Eliminato"
  - `email` = `deleted-<id>@quackfit.local`
  - password = hash di un valore casuale (nessuno potrà più fare login)
  - `foto_profilo` = null
  - `deleted_at` = adesso
  - logout ovunque
- La **Streak** viene eliminata definitivamente.
- I **CodiceVerifica** dell'utente vengono eliminati.
- I **tentativi** restano, collegati all'utente anonimizzato.
- La foto su Cloudinary viene cancellata; se Cloudinary fallisce l'anonimizzazione va avanti
  comunque e l'errore finisce nel log.
- L'email originale torna libera: chi vuole può registrarsi di nuovo con quell'indirizzo.

---

## Test da prevedere
- GET: dati corretti; `lezioniCompletate` conta solo lezioni attive con tentativo SUPERATO (e una sola volta
  anche con più tentativi superati); streak mostrata 0 se `ultima_attivita` è più vecchia di ieri;
  risposta anche con email non verificata; utente senza Streak → 500 nel formato standard, senza dettagli interni.
- PATCH dati: valori validi, vuoti, oltre 50 caratteri.
- Password: attuale sbagliata, nuova non valida, nuova uguale alla vecchia, token precedenti rifiutati dopo il cambio;
  il 21° tentativo dallo stesso IP nello stesso minuto → 429.
- Avatar: formato valido, formato non ammesso, file oltre 2 MB, file con estensione falsa, sostituzione della foto.
- Cambio email: password sbagliata, stessa email, email già usata (alla richiesta e alla conferma),
  reinvio prima di 60 secondi, codice giusto/sbagliato/scaduto, email cambiata solo dopo la conferma, token precedenti rifiutati.
- Cancellazione: password sbagliata, SUPERADMIN bloccato, dati anonimizzati, streak e codici eliminati,
  tentativi ancora presenti, login impossibile dopo la cancellazione, email di nuovo registrabile.
- Utente con email non verificata: 403 su tutti gli endpoint tranne GET /api/me e verifica.
