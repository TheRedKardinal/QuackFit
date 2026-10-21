# QuackFit

Web app **mobile first** in stile Duolingo/Mimo dedicata al fitness. Non traccia gli allenamenti: insegna, con
lezioni brevi, la teoria di base, l'anatomia essenziale, la tecnica degli esercizi e smonta i falsi miti. Ad
accompagnare l'utente c'è **Coach Quack**, un papero atletico disegnato in SVG.

Progetto Capstone del corso Epicode.

**Sito online:** https://quackfit.onrender.com
(il backend usa il piano gratuito di Render e dopo 15 minuti senza richieste si addormenta: al primo accesso può
servire circa un minuto perché risponda).

## Funzionalità
- **Account**: registrazione con codice di verifica via email, login, recupero e reimpostazione della password,
  limite di richieste per indirizzo IP e blocco temporaneo dopo troppi login sbagliati.
- **Percorso**: tappe e lezioni in ordine, con lezioni completate, da fare e bloccate.
- **Lezione**: domande Vero o falso, Scelta multipla e Abbinamento, una alla volta, con spiegazione dopo ogni
  risposta; le domande sbagliate tornano a fine giro; la lezione fallisce al terzo errore.
- **Streak**: giorni consecutivi con almeno una lezione completata, record e tracker della settimana.
- **Resoconto** di ogni tentativo, con le risposte date e quelle corrette.
- **Profilo**: statistiche, modifica di nome e cognome, password, email (con codice) e foto, cancellazione dell'account.
- **Admin**: gestione degli utenti (ricerca, promozione e retrocessione) e dei contenuti (tappe, lezioni e domande,
  con riordino e cancellazione).

## Stack
Versioni lette da `backend/pom.xml` e `frontend/package.json`.

| Parte | Tecnologie |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, Mail, Thymeleaf per i template delle email), JJWT 0.13.0, Lombok |
| Database | PostgreSQL (H2 in memoria solo per i test) |
| Frontend | React 19.2, TypeScript 6.0, Vite 8.3, Redux Toolkit 2.13, React Router 7.18, Bootstrap 5.3 (solo CSS), CSS Modules |
| Font | Nunito Sans (`@fontsource/nunito-sans` 5.3) |
| Test | JUnit, Mockito e MockMvc (backend); Vitest 5, React Testing Library e user-event (frontend) |
| Controllo del codice | oxlint |
| Servizi online | Render (backend e sito), Neon (PostgreSQL), Brevo (email), Cloudinary (foto profilo) |

## Struttura del repository
```
QuackFit/
├── backend/        Spring Boot: codice, test, Dockerfile, env.properties.example
├── frontend/       React + TypeScript (Vite)
│   └── design/     schermate di riferimento (Stitch)
├── avvia-app.cmd   avvia backend e frontend in locale (Windows)
├── apri-ide.cmd    apre backend in IntelliJ IDEA e frontend in Visual Studio Code
└── LICENSE
```

## Avvio in locale
### Prerequisiti
- JDK 25 (Maven non serve: c'è il wrapper `mvnw`)
- Node.js con npm
- PostgreSQL con un database vuoto (le tabelle le crea l'applicazione all'avvio)

### Configurazione
1. **Backend**: copiare `backend/env.properties.example` in `backend/env.properties` (escluso da Git) e compilare i
   valori. In locale le email non partono: con `MAIL_MODALITA=log` (il valore predefinito) i codici di verifica si
   leggono nel log del backend.
2. **Frontend**: copiare `frontend/.env.example` in `frontend/.env.local` (escluso da Git). Contiene solo l'indirizzo
   del backend.

### Avvio
Su Windows basta `avvia-app.cmd` nella radice (anche con doppio clic): apre due finestre, una per il backend e una per
il frontend, e se mancano le dipendenze del frontend esegue prima `npm install`.

A mano:
```
cd backend
mvnw.cmd spring-boot:run        # http://localhost:8080

cd frontend
npm install
npm run dev                     # http://localhost:5173
```
Al primo avvio il backend crea il superadmin (dalle variabili `SUPERADMIN_*`) e inserisce i contenuti iniziali del
percorso (3 tappe, 9 lezioni, 45 domande) se il database è vuoto.

## Variabili d'ambiente
Solo i nomi e a cosa servono: i valori stanno in `backend/env.properties` in locale e nel pannello della piattaforma
online, mai nel repository.

**Backend**
| Variabile | A cosa serve |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | connessione a PostgreSQL |
| `JWT_SECRET` | segreto per firmare i token di accesso (almeno 32 caratteri) |
| `JWT_DURATA` | durata del token (facoltativa, predefinita 24 ore) |
| `FRONTEND_URL` | unica origine ammessa dal CORS (l'indirizzo del sito) |
| `SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD` | superadmin creato al primo avvio |
| `MAIL_MODALITA` | invio delle email: `log` (solo nel log, predefinito), `smtp` (Gmail, solo in locale) o `brevo` |
| `MAIL_MITTENTE`, `MAIL_NOME_MITTENTE` | indirizzo e nome del mittente delle email |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | solo con `smtp`: account Gmail e password per app |
| `BREVO_API_KEY` | solo con `brevo`: chiave API |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | caricamento della foto profilo (obbligatorie in produzione) |
| `PORT` | porta del server in produzione (la imposta Render) |

**Frontend**
| Variabile | A cosa serve |
| --- | --- |
| `VITE_API_URL` | indirizzo del backend; finisce nel bundle ed è pubblica |

## Test e controlli
```
cd backend
mvnw.cmd test

cd frontend
npm test
npm run lint
npm run build
```

## Deploy
- **Database**: PostgreSQL su Neon (regione Frankfurt, connessione diretta).
- **Backend**: servizio web su Render costruito dal `backend/Dockerfile`, in due fasi:
  1. compila il jar con il JDK 25 e il wrapper Maven (i test si eseguono in locale prima di ogni commit, non qui);
  2. lo esegue con il solo JRE 25, come utente senza privilegi, con il profilo `prod` e la memoria della JVM
     limitata per il piano gratuito (`JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC"`).

  Il servizio è stato creato dal **Blueprint `render.yaml`** (nella radice): un file che descrive il servizio come
  codice, cioè tipo web, ambiente Docker, Root Directory `backend`, regione Frankfurt, piano gratuito e deploy
  automatico dal branch `main`. Nel file le variabili d'ambiente sono solo **dichiarate per nome**, senza valore
  (`sync: false`): i valori segreti si inseriscono dal pannello di Render e restano solo sulla piattaforma.
  Il profilo `prod` usa Brevo per le email, legge la porta da `PORT` e l'IP reale del client dall'header
  `True-Client-IP` di Render (per il limite di richieste per IP).
- **Sito**: Render Static Site creato dal pannello (non fa parte del Blueprint, che descrive solo il backend), con
  Root Directory `frontend`, Build Command `npm run build`, Publish Directory `dist`,
  `VITE_API_URL` impostata prima della build e una regola di rewrite `/*` → `/index.html` nel pannello, così un
  aggiornamento della pagina su una rotta interna non dà 404.
- **Email**: Brevo (mittente verificato). **Foto profilo**: Cloudinary.

## Copyright e licenza
© 2026 Claudio Giasi · Tutti i diritti riservati. Il codice può essere letto ma non copiato, modificato, distribuito né
riusato senza permesso scritto: vedi [LICENSE](LICENSE).

Le librerie di terze parti (elencate in `backend/pom.xml` e `frontend/package.json`) e il font Nunito Sans restano
soggetti alle rispettive licenze.
