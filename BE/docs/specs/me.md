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
- Le operazioni che richiedono la **password attuale** rispondono 400 `PASSWORD_ATTUALE_ERRATA` se è sbagliata.
- "Logout ovunque" = `token_validi_dal = adesso`: anche il token usato per la chiamata smette di funzionare,
  quindi il frontend deve cancellare il token e riportare l'utente al login.
- Codici email: regole comuni in `auth.md` (6 cifre, hash BCrypt, 15 minuti, 5 tentativi, 60 secondi tra due invii).

---

## Dati del profilo — GET /api/me
Risponde **200** con:
- `nome`, `cognome`, `email`, `ruolo`, `fotoProfilo` (URL o null)
- `emailVerificata`: serve al frontend per decidere se mostrare la schermata del codice; **non** va mostrato nel profilo
- `streak`: `valore` e `record`
- `lezioniCompletate`: numero di lezioni attive con almeno un tentativo SUPERATO dell'utente

Funziona anche con email non verificata.

### Streak calcolata in lettura
Lo streak salvato non viene modificato dalla lettura. Si calcola solo il valore da mostrare:
- `ultima_attivita` = oggi o ieri (fuso `Europe/Rome`) → `valore` salvato
- `ultima_attivita` null o più vecchia di ieri → `valore` mostrato = 0 (il `record` resta)

---

## Modifica dati — PATCH /api/me
Body: `nome`, `cognome`. Risponde **204**.

- `strip()` nel record; entrambi obbligatori, non vuoti, massimo **50 caratteri** (400 `VALIDAZIONE`).
- Email, ruolo e password non si cambiano da qui.

---

## Cambio password — PATCH /api/me/password
Body: `passwordAttuale`, `nuovaPassword`. Risponde **204**.

1. password attuale sbagliata → 400 `PASSWORD_ATTUALE_ERRATA`
2. nuova password non valida (stesse regole della registrazione) → 400 `VALIDAZIONE`
3. nuova password uguale all'attuale → 400 `VALIDAZIONE`
4. ok → nuovo hash BCrypt e **logout ovunque**

---

## Foto profilo — PATCH /api/me/avatar
Multipart, campo `file`. Risponde **200** con il nuovo URL (`fotoProfilo`).

- Formati: **jpg, png, webp**; massimo **2 MB**.
- Il tipo si controlla sul contenuto reale del file, non solo su estensione o content-type dichiarato.
- File mancante, vuoto, troppo grande o di tipo non ammesso → 400 `FILE_NON_VALIDO`.
- Su Cloudinary la foto ha un nome fisso legato all'utente (`avatar/<id utente>`):
  una nuova foto **sostituisce** la vecchia, non c'è nulla da conservare né da cancellare.
- Si salva in `foto_profilo` l'URL sicuro (https) restituito da Cloudinary.
- Se Cloudinary non risponde → 500 `ERRORE_INTERNO`; la foto precedente resta com'è.

---

## Cambio email
Due passaggi: l'email cambia solo quando il nuovo indirizzo è confermato con il codice.

### Richiesta — POST /api/me/email
Body: `nuovaEmail`, `password`. Risponde **204**.

Controlli, in ordine:
1. password attuale sbagliata → 400 `PASSWORD_ATTUALE_ERRATA`
2. `nuovaEmail` non valida (`strip()`, minuscolo, formato) → 400 `VALIDAZIONE`
3. `nuovaEmail` uguale a quella attuale → 400 `VALIDAZIONE`
4. `nuovaEmail` già usata da un utente attivo → 409 `EMAIL_GIA_USATA`
5. meno di 60 secondi dall'ultimo invio di un codice CAMBIO_EMAIL → 429 `ATTENDI_REINVIO`

Se ok: genera un codice CAMBIO_EMAIL con `nuova_email` valorizzata (invalidando i precedenti dello stesso tipo)
e lo invia alla **nuova** email. L'email dell'utente resta quella vecchia.

### Conferma — POST /api/me/email/confirm
Body: `codice`. Risponde **204**.

1. verifica del codice CAMBIO_EMAIL (ordine dei controlli in `auth.md`)
2. se nel frattempo la nuova email è stata presa da un altro utente → 409 `EMAIL_GIA_USATA`
3. ok → `email` = `nuova_email`, `email_verificata = true`, **logout ovunque**

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
  risposta anche con email non verificata.
- PATCH dati: valori validi, vuoti, oltre 50 caratteri.
- Password: attuale sbagliata, nuova non valida, nuova uguale alla vecchia, token precedenti rifiutati dopo il cambio.
- Avatar: formato valido, formato non ammesso, file oltre 2 MB, file con estensione falsa, sostituzione della foto.
- Cambio email: password sbagliata, stessa email, email già usata (alla richiesta e alla conferma),
  reinvio prima di 60 secondi, codice giusto/sbagliato/scaduto, email cambiata solo dopo la conferma, token precedenti rifiutati.
- Cancellazione: password sbagliata, SUPERADMIN bloccato, dati anonimizzati, streak e codici eliminati,
  tentativi ancora presenti, login impossibile dopo la cancellazione, email di nuovo registrabile.
- Utente con email non verificata: 403 su tutti gli endpoint tranne GET /api/me e verifica.
