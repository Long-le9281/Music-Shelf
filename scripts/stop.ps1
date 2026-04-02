[CmdletBinding()]
param(
	[Parameter(Position = 0)]
	[string]$Action = "stop"
)

$ErrorActionPreference = "Stop"

function Show-Usage() {
	@"
Usage:
  .\stop.bat       Stop the packaged app started by .\run.bat
  .\stop.bat help  Show this help
"@ | Write-Host
}

$normalizedAction = $Action.Trim().ToLowerInvariant()
if ($normalizedAction -in @("help", "-h", "--help", "/?")) {
	Show-Usage
	exit 0
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runDir = Join-Path $repoRoot ".run"
$pidPath = Join-Path $runDir "recordshelf.pid"
$jarPath = Join-Path $repoRoot "backend\target\recordshelf.jar"
$jarName = [System.IO.Path]::GetFileName($jarPath)

$stopped = $false

if (Test-Path $pidPath) {
	$rawPid = (Get-Content $pidPath -Raw).Trim()
	if ($rawPid -match '^\d+$') {
		$process = Get-Process -Id ([int]$rawPid) -ErrorAction SilentlyContinue
		if ($process) {
			Stop-Process -Id $process.Id -Force
			Write-Host "Stopped packaged app process $($process.Id)." -ForegroundColor Green
			$stopped = $true
		}
	}
	Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
}

if (-not $stopped) {
	$javaProcesses = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" -ErrorAction SilentlyContinue |
		Where-Object {
			$_.CommandLine -and (
				$_.CommandLine.Contains($jarPath) -or
				$_.CommandLine -match [regex]::Escape($jarName)
			)
		}

	foreach ($javaProcess in $javaProcesses) {
		Stop-Process -Id $javaProcess.ProcessId -Force -ErrorAction SilentlyContinue
		Write-Host "Stopped packaged app process $($javaProcess.ProcessId)." -ForegroundColor Green
		$stopped = $true
	}
}

if (-not $stopped) {
	Write-Host "No packaged Elgooners app process was found." -ForegroundColor Yellow
}

