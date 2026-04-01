[CmdletBinding()]
param(
	[Parameter(Position = 0)]
	[string]$Action = "run",
	[switch]$Rebuild,
	[switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
	Write-Host "[Elgooners] $Message" -ForegroundColor Cyan
}

function Test-CommandAvailable([string]$Name) {
	return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Assert-CommandAvailable([string]$Name, [string]$InstallHint) {
	if (-not (Test-CommandAvailable $Name)) {
		throw "$Name is required. $InstallHint"
	}
}

function Get-ListeningProcessId([int]$Port) {
	$connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
	if ($connection) {
		return [int]$connection.OwningProcess
	}
	return $null
}

function Wait-ForEndpoint([string]$Url, [int]$TimeoutSeconds = 60) {
	$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
	while ((Get-Date) -lt $deadline) {
		try {
			$response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
			if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
				return $true
			}
		} catch {
			Start-Sleep -Seconds 1
		}
	}
	return $false
}

function Show-Usage() {
	@"
Usage:
  .\run.bat                 Launch the packaged app (builds it first if needed)
  .\run.bat rebuild         Rebuild the frontend + backend jar, then launch it
  .\run.bat dev             Start backend and frontend in separate developer terminals
  .\run.bat help            Show this help
"@ | Write-Host
}

$normalizedAction = $Action.Trim().ToLowerInvariant()
switch ($normalizedAction) {
	"" { $mode = "user" }
	"run" { $mode = "user" }
	"user" { $mode = "user" }
	"dev" { $mode = "dev" }
	"rebuild" { $mode = "user"; $Rebuild = $true }
	"help" { Show-Usage; exit 0 }
	default { throw "Unknown action '$Action'. Run .\run.bat help for usage." }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendDir = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"
$runDir = Join-Path $repoRoot ".run"
$jarPath = Join-Path $backendDir "target\recordshelf.jar"
$pidPath = Join-Path $runDir "recordshelf.pid"
$stdoutLog = Join-Path $runDir "recordshelf.out.log"
$stderrLog = Join-Path $runDir "recordshelf.err.log"
$frontendBuildIndex = Join-Path $frontendDir "build\index.html"

New-Item -ItemType Directory -Path $runDir -Force | Out-Null

if ($mode -eq "dev") {
	Assert-CommandAvailable "mvn" "Install Maven 3.9+ and add it to PATH."
	Assert-CommandAvailable "npm" "Install Node.js 18+ (includes npm) and add it to PATH."

	Write-Step "Opening backend developer terminal..."
	Start-Process -FilePath "powershell.exe" -ArgumentList @(
		"-NoExit",
		"-ExecutionPolicy", "Bypass",
		"-Command", "Set-Location '$backendDir'; mvn spring-boot:run"
	) | Out-Null

	Write-Step "Opening frontend developer terminal..."
	Start-Process -FilePath "powershell.exe" -ArgumentList @(
		"-NoExit",
		"-ExecutionPolicy", "Bypass",
		"-Command", "Set-Location '$frontendDir'; if (-not (Test-Path '.\\node_modules')) { npm install }; npm start"
	) | Out-Null

	if (-not $NoBrowser) {
		Write-Step "The React dev server will open at http://localhost:3000 once npm finishes starting."
	}
	exit 0
}

Assert-CommandAvailable "java" "Install Java 17+ and add it to PATH."

$existingPid = $null
if (Test-Path $pidPath) {
	$rawPid = (Get-Content $pidPath -Raw).Trim()
	if ($rawPid -match '^\d+$') {
		$existingPid = [int]$rawPid
	} else {
		Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
	}
}

$listenerPid = Get-ListeningProcessId -Port 8080
if ($listenerPid) {
	if ($existingPid -and $listenerPid -eq $existingPid) {
		Write-Step "The packaged app is already running at http://localhost:8080"
		if (-not $NoBrowser) {
			Start-Process "http://localhost:8080"
		}
		exit 0
	}

	$listenerProcess = Get-Process -Id $listenerPid -ErrorAction SilentlyContinue
	$processName = if ($listenerProcess) { $listenerProcess.ProcessName } else { "PID $listenerPid" }
	throw "Port 8080 is already in use by $processName. Stop that process first, or use .\run.bat dev if you meant to run the developer servers."
}

$needsPackageBuild = $Rebuild -or -not (Test-Path $jarPath)
if ($needsPackageBuild) {
	Write-Step "Preparing the packaged app jar..."

	if (Test-CommandAvailable "npm") {
		Write-Step "Building the frontend bundle..."
		Push-Location $frontendDir
		try {
			& npm install
			if ($LASTEXITCODE -ne 0) { throw "npm install failed." }
			& npm run build
			if ($LASTEXITCODE -ne 0) { throw "npm run build failed." }
		} finally {
			Pop-Location
		}
	} elseif (Test-Path $frontendBuildIndex) {
		Write-Step "npm was not found, so the launcher will use the existing frontend build in frontend\\build"
	} else {
		throw "npm is required to build the packaged frontend because frontend\\build is missing."
	}

	Assert-CommandAvailable "mvn" "Install Maven 3.9+ and add it to PATH."
	Write-Step "Packaging the Spring Boot jar..."
	Push-Location $repoRoot
	try {
		& mvn -f (Join-Path $backendDir "pom.xml") clean package -DskipTests
		if ($LASTEXITCODE -ne 0) { throw "Maven packaging failed." }
	} finally {
		Pop-Location
	}
}

if (-not (Test-Path $jarPath)) {
	throw "Expected jar was not created: $jarPath"
}

if (Test-Path $stdoutLog) { Remove-Item $stdoutLog -Force }
if (Test-Path $stderrLog) { Remove-Item $stderrLog -Force }

Write-Step "Starting the packaged app from $jarPath"
$process = Start-Process -FilePath "java" `
	-ArgumentList @("-jar", $jarPath) `
	-WorkingDirectory $repoRoot `
	-RedirectStandardOutput $stdoutLog `
	-RedirectStandardError $stderrLog `
	-PassThru

Set-Content -Path $pidPath -Value $process.Id

if (Wait-ForEndpoint -Url "http://localhost:8080/" -TimeoutSeconds 60) {
	Write-Step "Application is ready at http://localhost:8080"
	Write-Step "Logs: $stdoutLog"
	if (-not $NoBrowser) {
		Start-Process "http://localhost:8080"
	}
	exit 0
}

Start-Sleep -Seconds 1
if ($process.HasExited) {
	Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
	Write-Host "`nLast backend output:" -ForegroundColor Yellow
	if (Test-Path $stdoutLog) { Get-Content $stdoutLog -Tail 30 }
	if (Test-Path $stderrLog) { Get-Content $stderrLog -Tail 30 }
	throw "The packaged app exited before it finished starting."
}

throw "The packaged app did not become ready within 60 seconds. Check the logs in $runDir"


