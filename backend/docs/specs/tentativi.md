# Spec: Tentativi (svolgimento di una lezione)

## Obiettivo
L'utente svolge una lezione rispondendo alle domande una alla volta. A ogni risposta riceve subito
l'esito, la soluzione e la spiegazione. Può sbagliare al massimo **3 volte**: al terzo errore il tentativo è fallito.
Superare la lezione completa la lezione e aggiorna la streak.

Package: `tentativi`. Endpoint: vedi `progettazione.md` → Tentativi.
Il contenuto della lezione (senza soluzioni) arriva da `percorso.md`.
Tutti gli endpoint richiedono l'utente autenticato con email verificata.

Costante: `MAX_ERRORI = 3`.

---

## Regole generali
- Il backend è l'unico a conoscere le soluzioni: il frontend le riceve solo dopo aver inviato una risposta.
- Un tentativo è visibile e utilizzabile **solo dal suo proprietario**. Un tentativo di un altro utente
  o inesistente → 404 `NON_TROVATO` (non si rivela che esiste).
- Un tentativo ha tre stati: `IN_CORSO` → `SUPERATO` oppure `FALLITO`. Chiuso non si riapre.
- Un tentativo chiuso non accetta più risposte né chiusure → 409 `TENTATIVO_CHIUSO`.
- Se la lezione viene cancellata mentre il tentativo è in corso, o non resta nessuna delle domande che
  c'erano all'avvio, le risposte e la chiusura rispondono 404 `NON_TROVATO`.
- Modifiche ai contenuti durante un tentativo in corso. Le domande del tentativo sono quelle **attive** e create
  **prima di `iniziato_at`** (`created_at` della domanda ≤ `iniziato_at`):
  - domanda **aggiunta** dopo l'avvio: non fa parte del tentativo, non conta per la chiusura e una risposta ad
    essa → 400 `VALIDAZIONE`; la vedrà chi avvia un nuovo tentativo;
  - domanda **cancellata** dopo l'avvio: non conta per la chiusura (anche se non era ancora risolta) e una risposta
    ad essa → 404 `NON_TROVATO`.
- Le date usano `Clock` (fuso `Europe/Rome` per i giorni della streak).

---

## Avvio — POST /api/attempts
Body: `lezioneId`. Risponde **201** con `tentativoId`.

Controlli, in ordine:
1. lezione inesistente o cancellata → 404 `NON_TROVATO`
2. lezione senza domande attive → 404 `NON_TROVATO`
3. lezione `BLOCCATA` per l'utente → 403 `LEZIONE_BLOCCATA` (stessa regola di `percorso.md`)

Se ok:
- eventuali tentativi `IN_CORSO` dello stesso utente sulla stessa lezione vengono chiusi come `FALLITO`
  (`finito_at = adesso`);
- si crea un nuovo tentativo: `IN_CORSO`, `errori = 0`, `iniziato_at = adesso`.

Si può avviare anche una lezione già `COMPLETATA` (si può rifare).

---

## Risposta — POST /api/attempts/{id}/answers
Si invia **una risposta alla volta**.

Body:
- `domandaId` (sempre)
- per `VERO_FALSO` e `SCELTA_MULTIPLA`: `opzioneId`
- per `ABBINAMENTO`: `coppie`, lista di `{ sinistroId, destroId }`

Validazioni (400 `VALIDAZIONE`):
- la domanda deve appartenere alla lezione del tentativo
- il campo giusto per il tipo di domanda (`opzioneId` oppure `coppie`) è presente e l'altro è assente
- l'opzione deve appartenere alla domanda
- abbinamento: le coppie devono contenere **tutti** gli elementi di sinistra della domanda, ognuno una sola volta,
  con elementi di destra tutti diversi tra loro e appartenenti alla stessa domanda

### Valutazione
- `VERO_FALSO` e `SCELTA_MULTIPLA`: giusta se l'opzione scelta ha `corretta = true`; se sbagliata `errori + 1`.
- `ABBINAMENTO`: una coppia è giusta se `sinistro.abbinamento_corretto_id == destro.id`;
  **ogni coppia sbagliata vale 1 errore**.
- `errori` non supera mai `MAX_ERRORI`. Quando raggiunge `MAX_ERRORI`: `esito = FALLITO`, `finito_at = adesso`.

### Domande riproposte
Le domande sbagliate vengono riproposte dal frontend a fine lezione. Per questo:
- si può rispondere **più volte alla stessa domanda** nello stesso tentativo;
- ogni risposta sbagliata, anche ripetuta, conta come errore;
- viene salvata (`RispostaOpzione` / `RispostaAbbinamento`) **solo la prima risposta** a ogni domanda
  (UNIQUE su tentativo + domanda); le successive non la modificano, tranne il campo `risolta`;
- `risolta` vale `true` se la prima risposta era giusta; se era sbagliata parte `false` e diventa `true`
  quando la domanda viene ripetuta correttamente (anche per l'abbinamento: tutte le coppie giuste;
  il valore è lo stesso su tutte le righe di quella domanda);
- una risposta a una domanda già `risolta = true` non è prevista: → 400 `VALIDAZIONE`.

### Risposta del server — 200
- `corretta`: boolean (per l'abbinamento, true solo se tutte le coppie sono giuste)
- `soluzione`: per VERO_FALSO e SCELTA_MULTIPLA l'id dell'opzione giusta;
  per ABBINAMENTO la lista delle coppie giuste
- `spiegazione`: testo della domanda
- `errori`: totale del tentativo
- `esito`: `IN_CORSO` oppure `FALLITO`

---

## Chiusura — POST /api/attempts/{id}/complete
Senza body. Chiude il tentativo come superato.

Controlli, in ordine:
1. tentativo già chiuso → 409 `TENTATIVO_CHIUSO`
2. non tutte le domande del tentativo hanno `risolta = true` → 400 `VALIDAZIONE`
   (domande del tentativo: attive e create prima dell'avvio, vedi "Regole generali")

Se ok:
- `esito = SUPERATO`, `finito_at = adesso`
- aggiornamento della **streak** (oggi = giorno corrente in `Europe/Rome`):
  - `ultima_attivita` = oggi → nessuna modifica
  - `ultima_attivita` = ieri → `valore + 1`
  - altrimenti (null o più vecchia) → `valore = 1`
  - dopo l'aggiornamento: `record = max(record, valore)` e `ultima_attivita = oggi`
- la streak cambia **solo** sui tentativi SUPERATI, mai sui FALLITI

Risponde **200** con `esito`, `errori` e la streak aggiornata (`valore`, `record`).

> Nota: il frontend gestisce il ciclo di riproposta delle domande sbagliate, ma è il backend a decidere
> quando la lezione è superata, grazie al campo `risolta`.

---

## Resoconto — GET /api/attempts/{id}
Funziona per qualsiasi stato del tentativo (anche `IN_CORSO`).

Risponde **200** con:
- `id`, `esito`, `errori`, `iniziatoAt`, `finitoAt`, titolo e id della lezione
- `domande`, nell'ordine stabilito dall'admin, **solo quelle già risposte**. Per ognuna:
  testo, tipo, la **prima risposta data**, se era corretta, la soluzione e la spiegazione

Il resoconto mostra anche domande o lezioni cancellate dopo il tentativo, perché le risposte salvate restano.

> Nota implementativa: i soft delete (`@SQLRestriction`) nascondono le entità cancellate nelle query normali **e anche
> nelle relazioni JPA** (da una risposta, `getDomanda()` su una domanda cancellata lancia `EntityNotFoundException`).
> Il resoconto usa quindi query SQL native in `ResocontoQuery` (`NamedParameterJdbcTemplate`, `RowMapper` e record),
> che raggiungono lezioni, domande, opzioni ed elementi cancellati. Il proprietario si controlla dentro la query
> (`t.utente_id`): un tentativo altrui non restituisce nulla → 404 `NON_TROVATO`.
> Anche avvio, risposta e chiusura non navigano da tentativi e risposte verso i contenuti: usano query con gli id
> (vedi `backend/CLAUDE.md`, Soft delete).

---

## Test da prevedere
- Avvio: lezione sbloccata, bloccata (403), inesistente o senza domande (404); tentativo IN_CORSO precedente
  chiuso come FALLITO; solo i tentativi dello stesso utente e della stessa lezione vengono chiusi.
- Risposta VERO_FALSO e SCELTA_MULTIPLA: giusta, sbagliata (errori + 1), terzo errore → FALLITO.
- Risposta ABBINAMENTO: tutte giuste; coppie sbagliate che valgono un errore ciascuna; errori limitati a `MAX_ERRORI`;
  coppie incomplete o con elementi di un'altra domanda → 400.
- Domanda ripetuta: viene salvata solo la prima risposta, gli errori delle successive vengono contati;
  una ripetizione corretta imposta `risolta = true`; una ripetizione sbagliata la lascia `false`;
  rispondere a una domanda già risolta → 400.
- Modifiche durante il tentativo: domanda aggiunta dopo l'avvio (risposta → 400, la chiusura la ignora);
  domanda cancellata (risposta → 404, la chiusura non la conta); lezione cancellata o tutte le domande cancellate → 404.
- Tentativo di un altro utente → 404; tentativo chiuso → 409 `TENTATIVO_CHIUSO` su risposte e chiusura.
- Domanda di un'altra lezione, opzione di un'altra domanda, campo sbagliato per il tipo → 400.
- Chiusura: con almeno una domanda non risolta (non risposta o sbagliata e non ripetuta) → 400; ok → SUPERATO; lezione sbloccata dopo il completamento.
- Streak: primo completamento (→ 1), stesso giorno (invariata), giorno dopo (+1), saltato un giorno (→ 1),
  aggiornamento del record; tentativo FALLITO non tocca la streak.
- Resoconto: tentativo chiuso e in corso; lezione o domanda cancellata dopo il tentativo; solo il proprietario.
