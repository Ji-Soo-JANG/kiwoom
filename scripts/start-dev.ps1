[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'

function Assert-Command {
    param([Parameter(Mandatory)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "필수 명령 '$Name'을 찾을 수 없습니다. 설치 후 다시 실행하세요."
    }
}

function Import-DotEnv {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw ".env 파일이 없습니다. '.env.example'을 복사하고 로컬 값을 입력하세요."
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) {
            continue
        }

        $parts = $trimmed.Split('=', 2)
        if ($parts.Count -ne 2) {
            throw ".env에 올바르지 않은 줄이 있습니다: $trimmed"
        }

        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
    }
}

Assert-Command docker
Assert-Command java
Assert-Command npm
Import-DotEnv -Path $envFile

$javaVersion = & java -version 2>&1 | Select-Object -First 1
if ($javaVersion -notmatch 'version "21[\.]') {
    throw "Java 21이 필요합니다. 현재 버전: $javaVersion"
}

if (-not $env:KIWOOM_APP_KEY -or $env:KIWOOM_APP_KEY -like 'replace-*') {
    throw '.env의 KIWOOM_APP_KEY를 실제 값으로 설정하세요.'
}
if (-not $env:KIWOOM_SECRET_KEY -or $env:KIWOOM_SECRET_KEY -like 'replace-*') {
    throw '.env의 KIWOOM_SECRET_KEY를 실제 값으로 설정하세요.'
}

$env:SPRING_PROFILES_ACTIVE = 'dev'

Push-Location $projectRoot
try {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Desktop이 실행 중이 아닙니다.'
    }

    docker compose -f compose.yml up -d --wait postgres
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL 컨테이너를 시작하지 못했습니다.'
    }

    Start-Process -FilePath 'powershell.exe' -WorkingDirectory $projectRoot `
        -ArgumentList @('-NoExit', '-Command', '.\mvnw.cmd spring-boot:run')
    Start-Process -FilePath 'powershell.exe' -WorkingDirectory (Join-Path $projectRoot 'frontend') `
        -ArgumentList @('-NoExit', '-Command', 'npm run dev')

    Write-Host 'PostgreSQL, Spring Boot, Vite 개발 서버를 시작했습니다.'
    Write-Host '로그인: http://localhost:8080/login'
    Write-Host 'React:  http://localhost:5173'
}
finally {
    Pop-Location
}
