@echo off
setlocal

set "ROOT=%~dp0"
set "BACKEND_SCRIPT=%ROOT%run-backend.bat"
set "FRONTEND_SCRIPT=%ROOT%run-frontend.bat"
set "SETUP_SCRIPT=%ROOT%setup.sh"
set "SETUP_MARKER=%ROOT%.setup-complete"

echo Elgooners launcher
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

if exist "%SETUP_SCRIPT%" if not exist "%SETUP_MARKER%" (
  echo.
  set /p RUN_SETUP=First-time setup available. Run setup now? [Y/N]:
  if /I "%RUN_SETUP%"=="Y" (
    where bash >nul 2>nul
    if errorlevel 1 (
      echo Bash was not found. Install Git Bash or use WSL, then run: sh setup.sh
    ) else (
      echo Running setup script...
      bash "%SETUP_SCRIPT%"
      if errorlevel 1 (
        echo Setup failed. Fix the reported issue and re-run run.bat.
        pause
        exit /b 1
      )
      echo setup completed on %date% %time%>"%SETUP_MARKER%"
    )
  )
)

call "%BACKEND_SCRIPT%" --check
if errorlevel 1 (
    echo.
    echo Environment check failed.
    if exist "%SETUP_SCRIPT%" (
        echo New user setup script available: "%SETUP_SCRIPT%"
        echo Run it in Git Bash or WSL: sh setup.sh
    )
    pause
    exit /b 1
)

call "%FRONTEND_SCRIPT%" --check
if errorlevel 1 (
    echo.
    echo Environment check failed.
    if exist "%SETUP_SCRIPT%" (
        echo New user setup script available: "%SETUP_SCRIPT%"
        echo Run it in Git Bash or WSL: sh setup.sh
    )
    pause
    exit /b 1
)

echo Starting backend on port 8080...
start "Elgooners Backend" cmd /k ""%BACKEND_SCRIPT%""

echo Waiting 20 seconds for the backend to start before launching the frontend...
timeout /t 20 /nobreak

echo Starting frontend on port 3000...
start "Elgooners Frontend" cmd /k ""%FRONTEND_SCRIPT%""

echo.
echo The backend and frontend are launching in separate windows.
echo Open http://localhost:3000 after the frontend finishes starting.
timeout /t 3 /nobreak >nul
start "" http://localhost:3000
pause
