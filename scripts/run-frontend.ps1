$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location (Join-Path $repoRoot "frontend")
try {
    if (-not (Test-Path "node_modules")) {
        Write-Host "node_modules not found. Installing dependencies..."
        npm install
    }

    Write-Host "Starting React frontend on http://localhost:3000"
    npm start
} finally {
    Pop-Location
}

