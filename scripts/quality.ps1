param(
  [switch]$Backend,
  [switch]$Frontend,
  [switch]$All,
  [string]$Base,
  [string]$BaseReport
)

$ErrorActionPreference = 'Stop'
if (-not ($Backend -or $Frontend -or $All)) { $All = $true }
if ($All) { $Backend = $true; $Frontend = $true }

function Invoke-QualityStep([string]$Label, [scriptblock]$Action) {
  Write-Host "QUALITY_STEP $Label"
  & $Action
  if ($LASTEXITCODE -ne 0) { throw "QUALITY_STEP_FAILED $Label exit=$LASTEXITCODE" }
}

function Invoke-Backend([string]$Root, [string]$Label) {
  Push-Location $Root
  try {
    Invoke-QualityStep "$Label Maven verify" { & .\mvnw.cmd --batch-mode clean verify }
  } finally { Pop-Location }
}

if ($Backend) {
  if ([string]::IsNullOrWhiteSpace($Base)) { $Base = (git rev-parse HEAD^).Trim() }
  if ([string]::IsNullOrWhiteSpace($Base)) { throw 'BASE_UNAVAILABLE' }

  Invoke-Backend (Get-Location).Path 'current'

  $checkerOut = Join-Path ([System.IO.Path]::GetTempPath()) ("kiwoom-coverage-" + [guid]::NewGuid())
  New-Item -ItemType Directory -Path $checkerOut | Out-Null
  try {
    Invoke-QualityStep 'checker compile' { & javac -d $checkerOut (Get-ChildItem tools\coverage\*.java | ForEach-Object FullName) }
    Invoke-QualityStep 'checker tests' { & java -cp $checkerOut ChangedCoverageCheckerTest }
    $report = (Resolve-Path target\site\jacoco\jacoco.xml).Path
    $effectiveBaseReport = $BaseReport
    $worktree = $null
    $compareCoverage = {
      Invoke-QualityStep 'changed and global coverage' {
        & java -cp $checkerOut ChangedCoverageChecker --repo . --base $Base --report $report --base-report $effectiveBaseReport
      }
    }
    if ([string]::IsNullOrWhiteSpace($effectiveBaseReport)) {
      $worktree = Join-Path ([System.IO.Path]::GetTempPath()) ("kiwoom-base-" + [guid]::NewGuid())
      try {
        Invoke-QualityStep 'base worktree' { & git worktree add --detach $worktree $Base }
        Invoke-Backend $worktree 'base'
        $effectiveBaseReport = Join-Path $worktree 'target\site\jacoco\jacoco.xml'
        & $compareCoverage
      } finally {
        & git worktree remove --force $worktree
      }
    } else {
      & $compareCoverage
    }
  } finally {
    Remove-Item -LiteralPath $checkerOut -Recurse -Force -ErrorAction SilentlyContinue
  }
}

if ($Frontend) {
  Push-Location (Join-Path (Get-Location).Path 'frontend')
  try {
    Invoke-QualityStep 'frontend npm ci' { & npm ci }
    Invoke-QualityStep 'frontend audit' { & npm audit --audit-level=high }
    Invoke-QualityStep 'frontend format' { & npm run format:check }
    Invoke-QualityStep 'frontend lint' { & npm run lint }
    Invoke-QualityStep 'frontend generated types' { & npm run types:check }
    Invoke-QualityStep 'frontend type usage' { & npm run types:usage }
    Invoke-QualityStep 'frontend coverage' { & npm run test:coverage }
    Invoke-QualityStep 'frontend Playwright browser' { & npx playwright install --with-deps chromium }
    Invoke-QualityStep 'frontend e2e' { & npm run test:e2e }
    Invoke-QualityStep 'frontend build check' { & npm run build:check }
    Invoke-QualityStep 'frontend bundle size' { & npm run build:size }
  } finally { Pop-Location }
}

Write-Host 'QUALITY_RESULT PASS'
