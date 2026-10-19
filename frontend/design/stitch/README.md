# Schermate di riferimento (Stitch)

Schermate esportate dal progetto Stitch **"QuackLingo Frontend Draft"** (ID `14737734096977436897`).

> **Solo riferimento visivo.** Il codice HTML esportato (Tailwind, CDN, icone esterne) **non si copia** nei componenti:
> si riscrive in React + TypeScript con le nostre variabili CSS (`src/styles/tokens.css`), la struttura per funzionalità
> e senza dipendenze nuove.
> Testi, numeri e dati delle schermate sono di esempio.

## Schermate

| Schermata Stitch | File | Schermata dell'app | Task |
| --- | --- | --- | --- |
| Registrazione | `registrazione.png` / `.html` | Registrazione | 20 |
| Login | `login.png` / `.html` | Login | 20 |
| Verifica Codice Email | `verifica-email.png` / `.html` | Verifica email con codice | 21 |
| Mappa Percorso | `mappa-percorso.jpg` / `.html` | Mappa delle tappe e lezioni (con barra di navigazione in basso) | 22, 42 |
| Quiz Lezione | `quiz-lezione.jpg` / `.html` | Svolgimento della lezione, drawer dell'esito | 43, 44 |
| Lezione Completata & Streak | `lezione-completata-streak.jpg` / `.html` | Vittoria con streak e tracker della settimana | 45 |
| Lezione Non Superata | `lezione-non-superata.png` / `.html` | Sconfitta (terzo errore) | 45 |
| Resoconto Lezione | `resoconto-lezione.png` / `.html` | Resoconto ("Rivedi risposte") | 46 |
| Profilo Utente | `profilo-utente.jpg` / `.html` | Profilo (`/api/me`) | 22, 27 |
| Modifica Profilo | `modifica-profilo.png` / `.html` | Nome e cognome, password, avatar, cambio email, cancellazione | 27, 28 |
| Admin - Gestione Utenti | `admin-utenti-desktop.png` / `.html` | Admin utenti, desktop | 32 |
| Admin - Gestione Utenti (Mobile) | `admin-utenti-mobile.png` / `.html` | Admin utenti, mobile | 32 |
| Admin - Gestione Contenuti | `admin-contenuti-desktop.png` / `.html` | Admin tappe, lezioni e domande, desktop | 33, 34 |
| Admin - Gestione Contenuti (Mobile) | `admin-contenuti-mobile.png` / `.html` | Admin tappe, lezioni e domande, mobile | 33, 34 |

Quattro screenshot sono in formato JPEG (Stitch li fornisce così), gli altri in PNG.

## Schermate previste senza riferimento

Si segue lo stile delle altre schermate.

- **Password dimenticata** e **reimpostazione della password** (task 21): usare come base Login e Verifica Codice Email.
- **Struttura dell'app e navigazione** (task 22): non c'è una schermata dedicata; la barra in alto (logo, streak, avatar)
  e la barra in basso (Percorso, Profilo) si ricavano da Mappa Percorso e Profilo Utente.

## Elementi fuori perimetro presenti nelle schermate

Le schermate contengono elementi che **non vanno realizzati**:

- XP e punti ("+15 XP", "420 XP", "Punti Quack"), livelli ("Livello Quack"), traguardi e badge
- "Esame tappa", "Prossimo capitolo", banner "Streak in pericolo" con pulsante "Salva"
- "Membro da …" nel profilo, etichette "LIVE", "+100% Focus", "Ritmo costante" e simili
- nel profilo valgono **solo** tre metriche: streak attuale, record, lezioni completate
- l'indicatore errori della lezione è "Errori x/3" con tre pallini: niente cuori né vite

## Colori

Le schermate usano anche tonalità non presenti tra i design token (rosso per errori e cancellazione,
verde scuro, marrone). Nei componenti si usano solo le variabili di `tokens.css`: se un colore manca,
va chiesto prima di aggiungerlo. Il rosso `#ba1a1a` (errori dei campi, drawer "risposta sbagliata") è già
stato aggiunto come `--colore-errore`.
