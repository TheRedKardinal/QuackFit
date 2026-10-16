@echo off
rem Apre il backend in IntelliJ IDEA e il frontend in Visual Studio Code.
rem I percorsi partono dalla cartella di questo file: funziona anche con doppio clic.
setlocal
set "RADICE=%~dp0"
set "IDEA=C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\bin\idea64.exe"
set "ERRORI=0"

if exist "%IDEA%" (
	start "" "%IDEA%" "%RADICE%backend"
) else (
	echo IntelliJ IDEA non trovato in: %IDEA%
	echo Aggiorna la variabile IDEA in questo file con il percorso giusto.
	set "ERRORI=1"
)

where code >nul 2>nul
if errorlevel 1 (
	echo Comando code di Visual Studio Code non trovato nel PATH.
	echo Reinstalla VS Code con l'opzione "Aggiungi a PATH".
	set "ERRORI=1"
) else (
	call code "%RADICE%frontend"
)

if "%ERRORI%"=="1" pause
