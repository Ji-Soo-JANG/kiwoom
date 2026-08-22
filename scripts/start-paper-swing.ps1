[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $projectRoot '.runtime'
$envFile = Join-Path $projectRoot '.env'
$pidFile = Join-Path $runtimeDir 'paper-swing.pid'
$logFile = Join-Path $runtimeDir 'paper-swing.log'
$errorLogFile = Join-Path $runtimeDir 'paper-swing-error.log'

if (-not (Test-Path -LiteralPath $envFile)) { throw '.env 파일이 없습니다.' }
foreach ($line in Get-Content -LiteralPath $envFile) {
    $value = $line.Trim()
    if (-not $value -or $value.StartsWith('#')) { continue }
    $parts = $value.Split('=', 2)
    if ($parts.Count -eq 2) {
        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
    }
}

$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:TRADING_MODE = 'PAPER'
$env:TRADING_SCHEDULER_ENABLED = 'true'
$env:SWING_MONITOR_ENABLED = 'true'

New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
if (Test-Path -LiteralPath $pidFile) {
    $existingPid = [int](Get-Content -LiteralPath $pidFile)
    if (Get-Process -Id $existingPid -ErrorAction SilentlyContinue) {
        Write-Host "PAPER 스윙 서버가 이미 실행 중입니다. PID=$existingPid"
        exit 0
    }
    Remove-Item -LiteralPath $pidFile -Force
}

Push-Location $projectRoot
try {
    docker compose -f compose.yml up -d --wait postgres
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL을 시작하지 못했습니다.' }

    & .\mvnw.cmd package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw '서버 빌드에 실패했습니다.' }

    $jar = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'target') -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
    if (-not $jar) { throw '실행할 서버 JAR를 찾을 수 없습니다.' }

    $process = Start-Process -FilePath 'java.exe' -ArgumentList @('-jar', $jar.FullName) `
        -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $logFile -RedirectStandardError $errorLogFile
    Set-Content -LiteralPath $pidFile -Value $process.Id
    Write-Host "PAPER 스윙 서버를 시작했습니다. PID=$($process.Id)"
}
finally { Pop-Location }
