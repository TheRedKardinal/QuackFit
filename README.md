# QuackFit

Web app mobile first in stile Duolingo dedicata al fitness: lezioni brevi di teoria, anatomia di base,
tecnica degli esercizi e falsi miti da smontare.

## Struttura del repository
```
QuackFit/
├── backend/    Spring Boot (Java 25, PostgreSQL)
└── frontend/   React + TypeScript (Vite)
    └── design/ schermate di riferimento (Stitch)
```

## Avvio in locale (Windows)
Due script nella radice, da lanciare anche con doppio clic:
- `avvia-app.cmd` → avvia backend (`mvnw.cmd spring-boot:run`) e frontend (`npm run dev`) in due finestre separate.
  Richiede `backend/env.properties` (copia di `env.properties.example` con le chiavi compilate);
  se mancano le dipendenze del frontend esegue prima `npm install`.
- `apri-ide.cmd` → apre `backend/` in IntelliJ IDEA e `frontend/` in Visual Studio Code.
  Il percorso di IntelliJ è nella variabile `IDEA` dello script: va adattato se è installato altrove.
