[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path (Join-Path $projectRoot '.runtime') 'paper-swing.pid'

if (Test-Path -LiteralPath $pidFile) {
    $serverPid = [int](Get-Content -LiteralPath $pidFile)
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $serverPid" -ErrorAction SilentlyContinue
    if ($process -and $process.Name -like 'java*' -and $process.CommandLine -like '*kiwoom*') {
        Stop-Process -Id $serverPid -Force
        Write-Host "PAPER 스윙 서버를 종료했습니다. PID=$serverPid"
    }
    Remove-Item -LiteralPath $pidFile -Force
}

Push-Location $projectRoot
try {
    docker compose -f compose.yml stop postgres
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL 종료에 실패했습니다.' }
    Write-Host 'PostgreSQL 컨테이너를 종료했습니다.'
}
finally { Pop-Location }
