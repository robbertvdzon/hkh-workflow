# Haalt de Windows-service hkh-workflow weg. De mappen app, updates, config en logs en alle instellingen blijven staan.
# Draai dit als administrator.   .\uninstall.ps1 [-Map C:\zcbs-workflow] [-Poort 9000] [-Firewall]
# Alleen ASCII in dit bestand (Windows PowerShell 5.1).

param(
    [string]$Map = 'C:\zcbs-workflow',
    [int]$Poort = 9000,
    [switch]$Firewall
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$exe = Join-Path $Map 'service\hkh-workflow.exe'

if (Get-Service hkh-workflow -ErrorAction SilentlyContinue) {
    & $exe stop 2>&1 | Out-Null
    & $exe uninstall 2>&1 | Out-Null
    Write-Host 'Service gestopt en verwijderd.'
} else {
    Write-Host 'De service bestaat niet.'
}
if ($Firewall) {
    Remove-NetFirewallRule -DisplayName "hkh-workflow ($Poort)" -ErrorAction SilentlyContinue
    Write-Host 'Firewallregel verwijderd.'
}
