@echo off
setlocal EnableDelayedExpansion

set "ROOT=%~dp0"
set "FRONTEND=%ROOT%frontend"
set "SETUP_SCRIPT=%ROOT%setup.sh"

echo Elgooners frontend launcher
echo Root: %ROOT%

where npm >nul 2>nul
if errorlevel 1 (
    echo npm was not found on PATH.
    if exist "%SETUP_SCRIPT%" (
        set /p RUN_SETUP=Run setup.sh now? [Y/N]:
        if /I "!RUN_SETUP!"=="Y" (
            where bash >nul 2>nul
            if errorlevel 1 (
                echo Bash was not found. Install Git Bash or use WSL, then run: sh setup.sh
            ) else (
                bash "%SETUP_SCRIPT%"
            )
        )
    )
    exit /b 1
)

if not exist "%FRONTEND%\package.json" (
    echo Frontend package.json was not found at "%FRONTEND%\package.json".
    exit /b 1
)

if /I "%~1"=="--check" (
    echo Frontend checks passed.
    exit /b 0
)

pushd "%FRONTEND%" >nul
if errorlevel 1 (
    echo Could not open frontend folder: "%FRONTEND%".
    exit /b 1
)

if not exist "node_modules" (
    echo Installing frontend dependencies...
    npm install
    if errorlevel 1 (
        set "EXIT_CODE=%ERRORLEVEL%"
        popd >nul
        exit /b %EXIT_CODE%
    )
)

echo Starting frontend on http://localhost:3000 ...
npm start
set "EXIT_CODE=%ERRORLEVEL%"

popd >nul
exit /b %EXIT_CODE%
