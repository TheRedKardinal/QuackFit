# Spec: Percorso (mappa e contenuto delle lezioni)

## Obiettivo
L'utente vede la mappa delle tappe e delle lezioni con il proprio stato di avanzamento
e può aprire il contenuto di una lezione sbloccata.

Package: `contenuti`. Endpoint: vedi `progettazione.md` → Percorso.
Tutti gli endpoint richiedono l'utente autenticato con email verificata.
La gestione dei contenuti da parte degli admin è in `admin.md`; i tentativi sono in `tentativi.md`.

Contano solo tappe, lezioni e domande **attive** (`deleted_at = null`): il filtro è automatico
grazie a `@SQLRestriction`.

---

## Stato delle lezioni
Lo stato **non è salvato**: si calcola ogni volta per l'utente loggato.

- `COMPLETATA`: l'utente ha almeno un tentativo SUPERATO su quella lezione.
- `SBLOCCATA`: non completata, ma la lezione che la precede nel percorso è completata
  (la prima lezione del percorso è sempre sbloccata).
- `BLOCCATA`: tutte le altre.

### Ordine del percorso
Lo sblocco è sequenziale su **tutto** il percorso: prima per `ordine` della tappa, poi per `ordine` della lezione.
L'ultima lezione di una tappa sblocca la prima della tappa successiva.

Poiché lo stato è calcolato, se un admin inserisce, sposta o cancella una lezione il percorso si ricalcola da solo
per tutti gli utenti. Conseguenza voluta: una lezione nuova inserita in mezzo può risultare `SBLOCCATA`
anche a chi aveva già completato quelle successive.

Una lezione `COMPLETATA` può essere rifatta quando si vuole.

---

## Mappa — GET /api/path/stages
Risponde **200** con la lista delle tappe in ordine. Per ogni tappa:
- `id`, `titolo`, `descrizione`, `ordine`
- `lezioni`, in ordine, ciascuna con `id`, `titolo`, `descrizione`, `ordine`, `stato`

Regole:
- Le tappe senza lezioni attive **non compaiono**.
- Non si restituiscono domande, né il loro numero.

---

## Contenuto di una lezione — GET /api/path/lessons/{id}
Risponde **200** con la lezione (`id`, `titolo`, `descrizione`) e le sue domande.

Errori:
- lezione inesistente o cancellata → 404 `NON_TROVATO`
- lezione senza domande attive → 404 `NON_TROVATO` (non si può avviare)
- lezione `BLOCCATA` per l'utente → 403 `LEZIONE_BLOCCATA`

### Domande
Arrivano **mescolate** dal backend a ogni richiesta (l'`ordine` stabilito dall'admin serve solo per la gestione
e per il resoconto, che le mostra nell'ordine dell'admin). Ogni domanda ha: `id`, `testo`, `tipo`.
**Mai** soluzioni, spiegazioni, né il campo `corretta`.
Il frontend percorre la lista così come arriva; non dipende dall'ordine.

- `VERO_FALSO`: le due opzioni (`id`, `testo`) nell'ordine fisso dell'admin (Vero, Falso).
- `SCELTA_MULTIPLA`: opzioni (`id`, `testo`) **mescolate** dal backend a ogni richiesta.
- `ABBINAMENTO`: due liste, `sinistra` e `destra` (ognuna con `id`, `testo`), **entrambe mescolate** dal backend
  a ogni richiesta. Non si restituisce `abbinamento_corretto_id`.

Il mescolamento avviene ad ogni richiesta, così chi rifà la lezione non può memorizzare lo schema delle posizioni.

---

## Note
- Il frontend non deve mai poter ricavare la soluzione da questi endpoint: le soluzioni e le spiegazioni
  arrivano solo dopo aver inviato una risposta (`tentativi.md`).
- Le DTO sono record separati da quelli dell'admin, che invece contengono le soluzioni.

## Test da prevedere
- Stato: prima lezione sbloccata per un nuovo utente; completare una lezione sblocca la successiva;
  passaggio tra l'ultima lezione di una tappa e la prima della successiva; lezione completata resta `COMPLETATA`;
  tentativo FALLITO non completa.
- Tappa senza lezioni attive assente dalla mappa; lezione cancellata assente e non conteggiata nello sblocco.
- Lezione bloccata → 403 `LEZIONE_BLOCCATA`; inesistente, cancellata o senza domande → 404 `NON_TROVATO`.
- La risposta non contiene mai `corretta`, `abbinamento_corretto_id` né spiegazioni.
- Domande: tutte presenti, nessuna duplicata, indipendentemente dall'ordine restituito.
- Abbinamento: entrambe le liste contengono tutti gli elementi; scelta multipla: tutte le opzioni presenti.
- Utente con email non verificata → 403 `EMAIL_NON_VERIFICATA`; senza token → 401 `NON_AUTENTICATO`.
