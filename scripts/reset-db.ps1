[CmdletBinding()]
param([switch]$ConfirmReset)

$ErrorActionPreference = 'Stop'
if (-not $ConfirmReset) {
    throw '모든 로컬 DB 데이터가 삭제됩니다. -ConfirmReset 옵션을 함께 사용하세요.'
}

$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    docker compose -f compose.yml down -v
    if ($LASTEXITCODE -ne 0) {
        throw '기존 PostgreSQL 볼륨 삭제에 실패했습니다.'
    }
    docker compose -f compose.yml up -d --wait postgres
    if ($LASTEXITCODE -ne 0) {
        throw '새 PostgreSQL 컨테이너 시작에 실패했습니다.'
    }
    Write-Host '로컬 PostgreSQL 데이터베이스를 초기화했습니다. 백엔드를 시작해 Flyway를 적용하세요.'
}
finally {
    Pop-Location
}
