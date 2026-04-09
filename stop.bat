@echo off
setlocal

echo Music Shelf — stopping backend (port 8080) and frontend (port 3000)...

set "KILLED_ANY=0"

:: --- Stop backend on port 8080 ---
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":8080 " ^| findstr "LISTENING"') do (
    echo Killing backend process (PID %%P) on port 8080...
    taskkill /F /PID %%P >nul 2>&1
    set "KILLED_ANY=1"
)

:: --- Stop frontend on port 3000 ---
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":3000 " ^| findstr "LISTENING"') do (
    echo Killing frontend process (PID %%P) on port 3000...
    taskkill /F /PID %%P >nul 2>&1
    set "KILLED_ANY=1"
)

:: --- Close the named terminal windows opened by run.bat ---
taskkill /F /FI "WINDOWTITLE eq Music Shelf Backend" >nul 2>&1
taskkill /F /FI "WINDOWTITLE eq Music Shelf Frontend" >nul 2>&1

if "%KILLED_ANY%"=="1" (
    echo Done. Both services have been stopped.
) else (
    echo No processes found listening on ports 8080 or 3000.
)

pause

