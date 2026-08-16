[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

Push-Location $projectRoot
try {
    docker compose -f compose.yml stop postgres
    Write-Host 'PostgreSQL 컨테이너를 중지했습니다.'
    Write-Host 'Spring Boot와 Vite 터미널은 각각 Ctrl+C로 종료하세요.'
}
finally {
    Pop-Location
}
