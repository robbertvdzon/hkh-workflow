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

if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Draai dit script als administrator (rechtsklik op verwijderen.cmd, Als administrator uitvoeren).'
}

if (Get-Service hkh-workflow -ErrorAction SilentlyContinue) {
    if (Test-Path $exe) {
        & $exe stop 2>&1 | Out-Null
        & $exe uninstall 2>&1 | Out-Null
    } else {
        # De service staat ergens anders dan in $Map; dan via Windows zelf.
        Stop-Service hkh-workflow -Force -ErrorAction SilentlyContinue
        & sc.exe delete hkh-workflow | Out-Null
    }
    # Wachten tot WinSW en java echt weg zijn, zodat de map daarna hernoemd of verwijderd kan worden.
    for ($i = 0; $i -lt 30; $i++) {
        $nog = @(Get-Process hkh-workflow -ErrorAction SilentlyContinue) + @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*current.jar*' })
        if ($nog.Count -eq 0) { break }
        if ($i -eq 15) { $nog | ForEach-Object { if ($_.ProcessId) { Stop-Process -Id $_.ProcessId -Force } else { $_ | Stop-Process -Force } } }
        Start-Sleep -Seconds 1
    }
    Write-Host 'Service gestopt en verwijderd.'
} else {
    Write-Host 'De service bestaat niet.'
}
if ($Firewall) {
    Remove-NetFirewallRule -DisplayName "hkh-workflow ($Poort)" -ErrorAction SilentlyContinue
    Write-Host 'Firewallregel verwijderd.'
}
Write-Host "De map $Map met instellingen en logs staat er nog."
