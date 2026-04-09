@echo off
setlocal

set "ROOT=%~dp0"
set "BACKEND_SCRIPT=%ROOT%run-backend.bat"
set "FRONTEND_SCRIPT=%ROOT%run-frontend.bat"

echo Music Shelf launcher
echo Root: %ROOT%

if not exist "%BACKEND_SCRIPT%" (
	echo Backend launcher was not found at "%BACKEND_SCRIPT%".
	pause
	exit /b 1
)

if not exist "%FRONTEND_SCRIPT%" (
	echo Frontend launcher was not found at "%FRONTEND_SCRIPT%".
	pause
	exit /b 1
)

if /I "%~1"=="--check" (
	call "%BACKEND_SCRIPT%" --check
	if errorlevel 1 exit /b 1
	call "%FRONTEND_SCRIPT%" --check
	if errorlevel 1 exit /b 1
	echo Combined checks passed.
	exit /b 0
)

echo Starting backend on port 8080...
start "Music Shelf Backend" cmd /k ""%BACKEND_SCRIPT%""

echo Waiting 20 seconds for the backend to start before launching the frontend...
timeout /t 20 /nobreak

echo Starting frontend on port 3000...
start "Music Shelf Frontend" cmd /k ""%FRONTEND_SCRIPT%""

echo.
echo The backend and frontend are launching in separate windows.
echo Open http://localhost:3000 after the frontend finishes starting.
timeout /t 3 /nobreak >nul
start "" http://localhost:3000
pause
