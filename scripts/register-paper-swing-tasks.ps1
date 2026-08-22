[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$startScript = Join-Path $PSScriptRoot 'start-paper-swing.ps1'
$stopScript = Join-Path $PSScriptRoot 'stop-paper-swing.ps1'
$powerShell = (Get-Command powershell.exe).Source

foreach ($path in @($startScript, $stopScript)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "스크립트를 찾을 수 없습니다: $path" }
}

$startAction = New-ScheduledTaskAction -Execute $powerShell `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$startScript`"" `
    -WorkingDirectory $projectRoot
$stopAction = New-ScheduledTaskAction -Execute $powerShell `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$stopScript`"" `
    -WorkingDirectory $projectRoot
$startTrigger = New-ScheduledTaskTrigger -Weekly -WeeksInterval 1 `
    -DaysOfWeek Monday, Tuesday, Wednesday, Thursday, Friday -At '08:40'
$stopTrigger = New-ScheduledTaskTrigger -Weekly -WeeksInterval 1 `
    -DaysOfWeek Monday, Tuesday, Wednesday, Thursday, Friday -At '15:40'
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopOnIdleEnd `
    -ExecutionTimeLimit (New-TimeSpan -Hours 8)

Register-ScheduledTask -TaskName 'Kiwoom Paper Swing Start' -Action $startAction `
    -Trigger $startTrigger -Settings $settings -Description 'PAPER 스윙 서버 장전 시작' -Force | Out-Null
Register-ScheduledTask -TaskName 'Kiwoom Paper Swing Stop' -Action $stopAction `
    -Trigger $stopTrigger -Settings $settings -Description 'PAPER 스윙 서버 장후 종료' -Force | Out-Null

Write-Host '작업 스케줄러 등록 완료: 평일 08:40 시작, 15:40 종료'
