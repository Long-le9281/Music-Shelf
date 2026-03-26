param(
    [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Write-Host "Repo root: $repoRoot"

Push-Location $repoRoot
try {
    Write-Host "\n== Backend: clean + test =="
    Push-Location "backend"
    try {
        mvn clean test
    } finally {
        Pop-Location
    }

    if (-not $SkipFrontend) {
        Write-Host "\n== Frontend: install + build =="
        Push-Location "frontend"
        try {
            npm install
            npm run build
        } finally {
            Pop-Location
        }
    }

    Write-Host "\nBuild/test script completed successfully."
} finally {
    Pop-Location
}

