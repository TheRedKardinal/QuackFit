@echo off
rem Avvia backend (Spring Boot) e frontend (Vite) in due finestre separate.
rem I percorsi partono dalla cartella di questo file: funziona anche con doppio clic.
setlocal
set "RADICE=%~dp0"

if not exist "%RADICE%backend\env.properties" (
	echo Manca backend\env.properties: copia backend\env.properties.example e compila le chiavi.
	pause
	exit /b 1
)

if not exist "%RADICE%frontend\node_modules" (
	echo Dipendenze del frontend mancanti: eseguo npm install...
	pushd "%RADICE%frontend"
	call npm install
	if errorlevel 1 (
		popd
		echo npm install non riuscito.
		pause
		exit /b 1
	)
	popd
)

start "QuackFit backend" /d "%RADICE%backend" cmd /k mvnw.cmd spring-boot:run
start "QuackFit frontend" /d "%RADICE%frontend" cmd /k npm run dev
