[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
foreach ($name in @('Kiwoom Paper Swing Start', 'Kiwoom Paper Swing Stop')) {
    if (Get-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue) {
        Unregister-ScheduledTask -TaskName $name -Confirm:$false
        Write-Host "작업 스케줄러에서 제거했습니다: $name"
    }
}
