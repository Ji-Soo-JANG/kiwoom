[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BackupFile,
    [switch]$ConfirmRestore
)

$ErrorActionPreference = 'Stop'
if (-not $ConfirmRestore) {
    throw '복원은 기존 데이터를 덮어씁니다. -ConfirmRestore 옵션을 함께 사용하세요.'
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path

Push-Location $projectRoot
try {
    Get-Content -LiteralPath $resolvedBackup -Raw | docker compose -f compose.yml exec -T postgres `
        psql -v ON_ERROR_STOP=1 -U kiwoom -d kiwoom
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL 복원에 실패했습니다.'
    }
    Write-Host "복원 완료: $resolvedBackup"
}
finally {
    Pop-Location
}
