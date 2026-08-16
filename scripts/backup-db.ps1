[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'backups')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null
$backupPath = Join-Path $resolvedOutput ("kiwoom-{0}.sql" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))

Push-Location $projectRoot
try {
    $content = docker compose -f compose.yml exec -T postgres pg_dump -U kiwoom -d kiwoom `
        --clean --if-exists --no-owner --no-privileges
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL 백업에 실패했습니다.'
    }
    [System.IO.File]::WriteAllLines($backupPath, $content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "백업 완료: $backupPath"
}
finally {
    Pop-Location
}
