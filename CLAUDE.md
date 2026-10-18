# QuackFit – Regole comuni

Questo file vale per tutto il repository. Ogni cartella ha poi il suo CLAUDE.md con le regole specifiche:
- `backend/CLAUDE.md` → Spring Boot
- `frontend/CLAUDE.md` → React

Rispondi sempre in **italiano**.

## Il progetto
QuackFit è una web app mobile first in stile Duolingo/Mimo dedicata al fitness: non traccia gli allenamenti,
insegna teoria, anatomia di base e tecnica degli esercizi e smonta i falsi miti, con lezioni brevi.
È il Capstone finale del corso Epicode (versione "vanilla"): tutto ciò che non è nella progettazione è fuori perimetro.

## Struttura del repository
```
QuackFit/
├── CLAUDE.md          ← questo file
├── docs/
│   └── task.md        ← elenco completo delle 52 task (stesso contenuto della board Trello)
├── backend/           ← Spring Boot (vedi backend/CLAUDE.md)
│   └── docs/
│       ├── progettazione.md   ← entità, enum, relazioni, elenco endpoint
│       └── specs/             ← una spec per feature: regole, casi limite, errori
└── frontend/          ← React (vedi frontend/CLAUDE.md)
```

## Regola d'oro: la progettazione è la fonte di verità
- Prima di scrivere codice per una task, leggi la spec della feature in `backend/docs/specs/` e la parte
  rilevante di `backend/docs/progettazione.md`.
- **Codice e documentazione si aggiornano insieme.** Se modifichi il comportamento di una feature,
  aggiorna anche la sua spec nella stessa task. Se cambia una spec, il codice va allineato.
- **Non prendere decisioni in autonomia** su tutto ciò che non è già scritto nella progettazione o nelle spec
  (nuovi campi, nuovi endpoint, librerie, cambi di comportamento, scelte di struttura).
  In questi casi **fermati e chiedi**, proponendo le opzioni con pro e contro.
- Se trovi una contraddizione tra spec, progettazione e codice, segnalala prima di procedere.

## Come lavoriamo
- **Una task alla volta**, presa da Trello. Non iniziare la successiva finché la precedente non è chiusa.
- Prima di scrivere codice, spiega in breve **cosa farai e quali file toccherai**, e aspetta l'ok.
- Lavora **a piccoli passi**, seguendo gli step della checklist della card Trello.
- Non modificare file che non riguardano la task in corso. Se serve, chiedi prima.
- Spiega le scelte non ovvie: questo progetto serve anche a imparare e va discusso all'esame.
- **Preferisci la soluzione più semplice**, la più vicina a quanto si vede nei corsi Spring Boot standard
  (`@Valid`, service, repository), a meno che non siano in gioco la sicurezza o la corruzione dei dati.
  Se una soluzione richiede strumenti nuovi (validator personalizzati, lock, async, SQL a mano), scrivilo nel piano
  con l'alternativa semplice accanto, in due righe, e lascia scegliere all'utente.

## Git
### Branch
- Non lavorare mai direttamente su `main`.
- Un branch per ogni task Trello, creato da `main` aggiornato:
  - `feature/<area>-<descrizione>` per le funzionalità (es. `feature/auth-registrazione`)
  - `fix/<area>-<descrizione>` per le correzioni
  - `chore/<descrizione>` per configurazione e contorno
  - `docs/<descrizione>` per la sola documentazione (es. `docs/readme-licenza`)
- Ogni task si chiude con una **Pull Request** verso `main`. Il **merge lo fa solo l'utente**.

### Commit
- Formato **Conventional Commits, in italiano**: `tipo(area): descrizione al presente`
  - `feat` nuova funzionalità · `fix` correzione · `test` test · `docs` documentazione e spec
  - `refactor` riorganizzazione senza cambiare comportamento · `chore` configurazione, dipendenze
  - Esempi: `feat(auth): registrazione con invio codice di verifica`, `test(streak): casi di streak scaduta`
- Commit piccoli e coerenti: uno per step significativo, non un unico commit enorme.
- **Proponi sempre il commit e aspetta l'approvazione**: mostra i file modificati e il messaggio proposto.
  Esegui `git commit` solo dopo l'ok esplicito dell'utente.
- **Nessuna attribuzione a Claude**: nessuna riga `Co-Authored-By`, nessun riferimento a Claude o a strumenti AI
  nei messaggi di commit e nelle descrizioni delle PR. L'autore è solo l'utente.
- Non fare `push`, non aprire PR e non fare merge senza ok esplicito.

## Trello
La board ha queste colonne: **Da fare → In corso → In revisione → Fatto**.
Ogni card è una task, con una checklist degli step. Board: "Quackfit - Tasks" (https://trello.com/b/eRV4nblg/quackfit-tasks).
Le card sono numerate 01–52 e sono **autosufficienti**: nella descrizione hanno branch, dipendenze, file da leggere e obiettivo.
Il testo completo di tutte le task è in `docs/task.md`: per riprendere il lavoro basta leggere la card e i file che indica,
senza dover ricostruire la storia del progetto.

- **Se è disponibile il connettore Trello** (strumenti `Trello`), usa quello per leggere la card, spostarla di colonna
  e spuntare la checklist. Niente credenziali da gestire.
- **Altrimenti** si usano le **API REST ufficiali di Trello** dal terminale (curl). Le credenziali sono nel file
  `.env.trello` nella radice del repository, **escluso da Git**:
  ```
  TRELLO_API_KEY=...
  TRELLO_TOKEN=...
  TRELLO_BOARD_ID=...
  ```
  Non stampare mai chiave e token nell'output e non scriverli in nessun altro file.
- Trello può rispondere 429 (troppe richieste): in quel caso aspetta qualche secondo e riprova, senza ripetere
  operazioni già riuscite.
- Le etichette colorate sono solo un aiuto visivo: blu = Backend, verde = Frontend, arancione = Docs/Deploy.
- Flusso di una task:
  1. inizio task → sposta la card in **In corso**
  2. step completato → spunta la voce nella checklist
  3. PR aperta → sposta la card in **In revisione**
  4. la card va in **Fatto** solo dopo che l'utente ha fatto il merge
- Non creare, eliminare o rinominare card o colonne senza ok dell'utente.

## Sicurezza
- Mai committare credenziali: `env.properties`, `.env*`, `.env.trello` devono essere nel `.gitignore`.
- Se una credenziale finisce per errore in un file tracciato, fermati e avvisa subito.
