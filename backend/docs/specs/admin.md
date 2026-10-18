# Spec: Admin (gestione utenti e contenuti)

## Obiettivo
Gli admin gestiscono gli utenti (lista e ruoli) e i contenuti del percorso (tappe, lezioni, domande).

Package: `utente` (gestione utenti) e `contenuti` (gestione contenuti). Endpoint: vedi `progettazione.md` → Admin.

## Regole comuni
- Tutti gli endpoint `/api/admin/**` sono accessibili solo a `ADMIN` e `SUPERADMIN`; gli altri ricevono 403 `ACCESSO_NEGATO`.
- Serve anche l'**email verificata**: un admin con email non verificata riceve 403 `EMAIL_NON_VERIFICATA`.
  Si controlla prima il ruolo e poi l'email, così un non admin riceve sempre `ACCESSO_NEGATO`.
  Il SUPERADMIN creato dal seeder ha l'email già verificata.
- Le DTO dell'admin sono separate da quelle dell'utente: **contengono le soluzioni** (`corretta`, coppie giuste).
- Testi con `strip()` nel record; tutti obbligatori e non vuoti, tranne dove indicato.
- Lunghezze massime (400 `VALIDAZIONE` se superate):
  | Campo | Max |
  | --- | --- |
  | titolo (tappa, lezione) | 100 |
  | descrizione (tappa, lezione) | 300 |
  | testo della domanda | 500 |
  | spiegazione | 1000 |
  | testo di un'opzione o di un elemento di abbinamento | 200 |
- Risorsa inesistente o già cancellata → 404 `NON_TROVATO`.
- **Cancellazioni a cascata** (tappa → lezioni → domande; lezione → domande): il service calcola l'istante una sola
  volta e lo usa per tutta la cascata; ciò che era già cancellato mantiene il suo `deleted_at`. Opzioni ed elementi di
  abbinamento non si cancellano mai (vedi `backend/CLAUDE.md`, Soft delete).

---

# Utenti

## Lista — GET /api/admin/users
Parametri: `page` (da 0, default 0), `size` (default 20, massimo 50), `q` (opzionale).

- Parametri mancanti → valori predefiniti (`page=0`, `size=20`).
- Parametri non validi → 400 `VALIDAZIONE`, con il parametro sbagliato in `campi`:
  - `page` negativa → *"La pagina non può essere negativa"*
  - `size` fuori da 1…50 → *"La dimensione deve essere tra 1 e 50"*
  - valore non numerico (es. `page=abc`, `size=abc`) → stesso 400 `VALIDAZIONE` (*"Valore non valido"*), non un errore diverso
- Solo utenti **attivi** (`deleted_at = null`), dal più recente al più vecchio (`created_at`; a parità di data
  decide l'`id`, così un utente non cambia pagina tra una richiesta e l'altra).
- `q`: ricerca senza distinzione tra maiuscole e minuscole, che cerca il testo dentro nome, cognome o email.
  Query parametrizzata, mai concatenata.
  - `q` vuoto o di soli spazi = nessuna ricerca; altrimenti il testo si cerca dopo `strip()`.
  - `%` e `_` si cercano come caratteri normali, non come jolly del `LIKE` (escape con `!`).
- Risponde **200** con una pagina: `contenuto` (lista), `pagina`, `dimensione`, `totaleElementi`, `totalePagine`.
- Ogni utente: `id`, `nome`, `cognome`, `email`, `ruolo`, `emailVerificata`, `creatoAt`.

## Cambio ruolo — PATCH /api/admin/users/{id}/role
Body: `ruolo` (`UTENTE` o `ADMIN`). Risponde **204**.

Controlli, in ordine:
1. utente inesistente o cancellato → 404 `NON_TROVATO`
2. `ruolo` = `SUPERADMIN` nel body → 403 `ACCESSO_NEGATO` (nessuno assegna SUPERADMIN)
3. l'utente da modificare è il chiamante stesso → 403 `ACCESSO_NEGATO`
4. l'utente da modificare è un SUPERADMIN → 403 `ACCESSO_NEGATO` (nessuno gli toglie il ruolo)
5. stesso ruolo che ha già → 204 senza modifiche e **senza** logout ovunque
6. permessi:

   | Chi chiama | UTENTE → ADMIN | ADMIN → UTENTE |
   | --- | --- | --- |
   | ADMIN | sì | no (403 `ACCESSO_NEGATO`) |
   | SUPERADMIN | sì | sì |

7. ok → ruolo aggiornato e **logout ovunque** per l'utente modificato (`token_validi_dal = adesso`),
   così il nuovo ruolo vale subito.

---

# Contenuti

## Ordine (tappe, lezioni, domande)
Il numero d'ordine è gestito **dal service**, senza vincolo UNIQUE nel database (il soft delete lo renderebbe un problema).
Gli ordini degli elementi attivi sono sempre **1, 2, 3… senza buchi**.

- **Creazione:** il nuovo elemento va in fondo (`max + 1`). L'ordine non si sceglie in creazione.
- **Riordino** (`PATCH` con `ordine`, solo tappe e lezioni): l'elemento va alla nuova posizione e gli altri scalano.
  Posizione fuori da `1…n` → 400 `VALIDAZIONE`.
- **Cancellazione:** gli elementi rimasti vengono rinumerati.
- Le **lezioni** si riordinano solo dentro la propria tappa: spostarle in un'altra tappa non è previsto.
- Le **domande** non si riordinano: l'utente le vede mescolate; l'ordine di creazione serve solo per il resoconto e la vista admin.
- **Admin in contemporanea**: ogni operazione che cambia l'ordine (creazione, riordino, cancellazione) blocca prima
  le righe con un lock pessimistico (`PESSIMISTIC_WRITE`) e poi rinumera, così due admin non lasciano ordini
  duplicati o buchi:
  - tappe: tutte le tappe attive, sempre in ordine di `ordine` e `id` (stessa sequenza ovunque, niente deadlock)
  - lezioni: la riga della tappa genitore, che esiste anche quando la tappa non ha lezioni
  - **Limite accettato**: due creazioni simultanee della **prima** tappa a tabella vuota non hanno righe da bloccare
    e possono uscire entrambe con ordine 1. Si sistema con un riordino.

## Tappe

### Elenco — GET /api/admin/stages
Risponde **200** con tutte le tappe attive in ordine. Per ogni tappa: `id`, `titolo`, `descrizione`, `ordine`
e le sue lezioni attive in ordine (`id`, `titolo`, `descrizione`, `ordine`, `numeroDomande`).

### Risposte di creazione e modifica (tappe e lezioni)
Un solo formato per risorsa, lo stesso dell'elenco:
- tappa: `id`, `titolo`, `descrizione`, `ordine`, `lezioni` (solo attive, in ordine; vuota alla creazione)
- lezione: `id`, `titolo`, `descrizione`, `ordine`, `tappaId`, `numeroDomande` (0 alla creazione)

La risposta basta a sostituire l'elemento nella lista **solo** per la modifica di `titolo` e `descrizione`.
Creazione, cancellazione e una PATCH con `ordine` cambiano la posizione anche degli altri elementi: il frontend
deve rifare `GET /api/admin/stages` invece di aggiornare la lista a mano (vale anche per le lezioni, che arrivano
dentro le tappe). Nessun formato di risposta aggiuntivo.

### Creazione — POST /api/admin/stages
Body: `titolo`, `descrizione`. Risponde **201** con la tappa creata, in fondo all'ordine.

### Modifica — PATCH /api/admin/stages/{stageId}
Body (tutti opzionali, `null`/assente = invariato): `titolo`, `descrizione`, `ordine`. Risponde **200** con la tappa.

Regole della PATCH (anche per le lezioni):
- un testo presente non può essere vuoto o di soli spazi → 400 `VALIDAZIONE` (*"Il titolo non può essere vuoto"*,
  *"La descrizione non può essere vuota"*); le lunghezze massime valgono come in creazione (*"Massimo N caratteri"*)
- `ordine` minore di 1 → 400 (*"La posizione deve essere almeno 1"*); maggiore del numero di elementi → 400
  (*"La posizione deve essere tra 1 e n"*), sempre con il campo `ordine` in `campi` e senza modificare nulla

### Cancellazione — DELETE /api/admin/stages/{stageId}
Risponde **204**. Soft delete (`deleted_at = adesso`) della tappa e, **a cascata**, delle sue lezioni e domande.
Le tappe rimaste vengono rinumerate.

## Lezioni

### Creazione — POST /api/admin/stages/{stageId}/lessons
Body: `titolo`, `descrizione`. Risponde **201** con la lezione, in fondo alla tappa.
Tappa inesistente o cancellata → 404 `NON_TROVATO`.

### Dettaglio — GET /api/admin/lessons/{lessonId}
Risponde **200** con la lezione (`id`, `titolo`, `descrizione`, `ordine`, `tappaId`) e **tutte** le sue domande
in ordine, complete di soluzioni (vedi "Formato di una domanda").

### Modifica — PATCH /api/admin/lessons/{lessonId}
Body (opzionali): `titolo`, `descrizione`, `ordine`. Risponde **200** con la lezione.

### Cancellazione — DELETE /api/admin/lessons/{lessonId}
Risponde **204**. Soft delete della lezione e, a cascata, delle sue domande. Le lezioni rimaste nella tappa
vengono rinumerate.

Conseguenza per gli utenti: una lezione cancellata non conta più né per lo sblocco né per `lezioniCompletate`,
e il percorso si ricalcola da solo (vedi `percorso.md`).

## Domande

### Creazione — POST /api/admin/lessons/{lessonId}/questions
Body: `testo`, `spiegazione`, `tipo` e i campi del tipo. Risponde **201** con la domanda creata, in fondo alla lezione.
Lezione inesistente o cancellata → 404 `NON_TROVATO`. Qualunque regola violata → 400 `VALIDAZIONE`.

| Tipo | Campi | Regole |
| --- | --- | --- |
| `VERO_FALSO` | `rispostaVero` (boolean) | Il backend crea le opzioni "Vero" e "Falso" e segna come corretta quella giusta |
| `SCELTA_MULTIPLA` | `opzioni`: lista di `{ testo, corretta }` | da 2 a 6 opzioni, **una sola** con `corretta = true` |
| `ABBINAMENTO` | `coppie`: lista di `{ sinistra, destra }` | da 2 a 6 coppie; il backend crea gli elementi SINISTRA e DESTRA e il collegamento corretto |

I campi di un tipo diverso da quello indicato devono essere assenti.

### Modifica — PATCH /api/admin/questions/{questionId}
Si modificano **solo i testi**. Risponde **200** con la domanda.

Body (tutti opzionali):
- `testo`, `spiegazione`
- `opzioni`: lista di `{ id, testo }` (SCELTA_MULTIPLA)
- `elementi`: lista di `{ id, testo }` (ABBINAMENTO)

Regole:
- gli `id` devono appartenere alla domanda, altrimenti 400 `VALIDAZIONE`
- gli elementi non inclusi restano invariati
- su `VERO_FALSO` i testi delle opzioni non si modificano ("Vero" e "Falso" sono fissi)
- **non si cambiano** `tipo`, quale opzione è corretta, il numero di opzioni o coppie né i collegamenti dell'abbinamento:
  la **struttura** si cambia cancellando e ricreando la domanda

### Cancellazione — DELETE /api/admin/questions/{questionId}
Risponde **204**. Soft delete della domanda; le altre vengono rinumerate.
Si può cancellare anche l'ultima domanda: la lezione resta, ma finché non ha domande non si può avviare
(404 `NON_TROVATO`, vedi `percorso.md`).

### Formato di una domanda (risposte admin)
- Comune: `id`, `testo`, `spiegazione`, `tipo`, `ordine`
- `VERO_FALSO` e `SCELTA_MULTIPLA`: `opzioni` in ordine, ognuna con `id`, `testo`, `corretta`
- `ABBINAMENTO`: `coppie` in ordine, ognuna con `sinistro` (`id`, `testo`) e `destro` (`id`, `testo`)
- I due campi `opzioni` e `coppie` ci sono sempre: quello che non riguarda il tipo è una lista vuota (`[]`, mai `null`).

---

## Note
- Modificare o cancellare contenuti non tocca i tentativi già svolti: restano leggibili nel resoconto (vedi `tentativi.md`).
- Il seeder (`seed`) inserisce i contenuti iniziali rispettando le stesse regole di ordine e di struttura.
- Le liste di tappe e lezioni sono piccole: nessuna paginazione, solo la lista utenti è paginata.

## Test da prevedere
- Lista utenti: paginazione e valori predefiniti; `size=0`, `size=51`, `page=-1`, `page=abc` → 400 `VALIDAZIONE` con il
  campo; ricerca con `q` (nome, cognome, email, maiuscole, `%` e `_` come testo); esclusione degli utenti cancellati.
- Cambio ruolo: ogni cella della tabella dei permessi; nessuno modifica sé stesso; nessuno assegna o toglie SUPERADMIN;
  stesso ruolo → 204 senza logout; utente cancellato → 404; token precedenti rifiutati dopo un cambio effettivo.
- Ordine: creazione in fondo; riordino verso l'alto e verso il basso; posizione fuori limite → 400;
  rinumerazione dopo una cancellazione; lezioni riordinate solo dentro la propria tappa; dopo ogni operazione,
  rileggendo l'elenco, gli ordini sono 1…n senza buchi né duplicati.
- Risposte di creazione e modifica: stessa forma dell'elenco (`lezioni` vuota per una tappa nuova, `numeroDomande` 0
  per una lezione nuova).
- Concorrenza: il lock non si prova su H2; verifica manuale su PostgreSQL con due sessioni.
- Cancellazione a cascata: tappa → lezioni e domande; lezione → domande; elementi nascosti agli utenti e alla mappa;
  tentativi precedenti ancora leggibili.
- Creazione domande: i tre tipi validi; troppe o poche opzioni; zero o due opzioni corrette; campi di un altro tipo;
  testi vuoti o troppo lunghi.
- Modifica domanda: solo i testi; id di un'altra domanda → 400; tentativo di cambiare struttura non possibile;
  opzioni di VERO_FALSO non modificabili.
- Permessi: un UTENTE su qualunque endpoint admin → 403 `ACCESSO_NEGATO` (anche se non verificato); senza token → 401;
  un ADMIN con email non verificata → 403 `EMAIL_NON_VERIFICATA`.
