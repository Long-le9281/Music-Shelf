@echo off
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "FRONTEND=%ROOT%frontend"

echo Music Shelf launcher
echo Root: %ROOT%

where java >nul 2>nul
if errorlevel 1 (
	echo Java was not found on PATH.
	pause
	exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
	echo Maven was not found on PATH.
	pause
	exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
	echo npm was not found on PATH.
	pause
	exit /b 1
)

if not exist "%BACKEND%\pom.xml" (
	echo Backend pom.xml was not found.
	pause
	exit /b 1
)

if not exist "%FRONTEND%\package.json" (
	echo Frontend package.json was not found.
	pause
	exit /b 1
)

echo Starting backend on port 8080...
start "Music Shelf Backend" powershell -NoExit -ExecutionPolicy Bypass -Command "& { Set-Location '%BACKEND%'; $env:JAVA_TOOL_OPTIONS='-Dnet.bytebuddy.experimental=true'; mvn -q -f pom.xml spring-boot:run }"

echo Starting frontend on port 3000...
start "Music Shelf Frontend" powershell -NoExit -ExecutionPolicy Bypass -Command "& { Set-Location '%FRONTEND%'; if (-not (Test-Path 'node_modules')) { npm install; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }; npm start }"

echo.
echo The backend and frontend are launching in separate windows.
echo Open http://localhost:3000 after the frontend finishes starting.
timeout /t 3 /nobreak >nul
start "" http://localhost:3000
pause
