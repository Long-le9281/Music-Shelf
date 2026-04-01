@echo off
setlocal

set "ROOT_DIR=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT_DIR%scripts\launch.ps1" %*
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
	echo.
	pause
)

exit /b %EXIT_CODE%

