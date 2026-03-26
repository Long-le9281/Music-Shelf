$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location (Join-Path $repoRoot "backend")
try {
    Write-Host "Starting Spring Boot backend on http://localhost:8080"
    mvn spring-boot:run
} finally {
    Pop-Location
}

