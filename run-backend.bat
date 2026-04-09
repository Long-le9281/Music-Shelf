@echo off
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"

echo Music Shelf backend launcher
echo Root: %ROOT%

where java >nul 2>nul
if errorlevel 1 (
    echo Java was not found on PATH.
    exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo Maven was not found on PATH.
    exit /b 1
)

if not exist "%BACKEND%\pom.xml" (
    echo Backend pom.xml was not found at "%BACKEND%\pom.xml".
    exit /b 1
)

if /I "%~1"=="--check" (
    echo Backend checks passed.
    exit /b 0
)

pushd "%BACKEND%" >nul
if errorlevel 1 (
    echo Could not open backend folder: "%BACKEND%".
    exit /b 1
)

set "JAVA_TOOL_OPTIONS=-Dnet.bytebuddy.experimental=true"
echo Starting backend on http://localhost:8080 ...
mvn -f pom.xml spring-boot:run
set "EXIT_CODE=%ERRORLEVEL%"

popd >nul
exit /b %EXIT_CODE%

