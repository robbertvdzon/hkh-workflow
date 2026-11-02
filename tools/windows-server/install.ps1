# Installeert hkh-workflow als Windows-service (met WinSW) en start hem. Draai dit als administrator.
# Opnieuw draaien mag: een bestaande service wordt eerst gestopt en opnieuw geinstalleerd; instellingen, logs en updates blijven staan.
#
#   .\install.ps1 -WinSw C:\pad\WinSW.NET461.exe -Jar C:\pad\hkh-workflow.jar
#   .\install.ps1 -WinSw ... -Jar ... -Account '.\hkhservice' -Wachtwoord 'geheim'     # onder een eigen account
#
# Alleen ASCII in dit bestand: Windows PowerShell 5.1 leest bestanden zonder BOM als ANSI.

param(
    [Parameter(Mandatory)][string]$WinSw,
    [string]$Jar,
    [string]$Map = 'C:\zcbs-workflow',
    [int]$Poort = 9000,
    [string]$Java,
    [string]$Account,
    [string]$Wachtwoord,
    [switch]$NietStarten
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$hier = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Draai dit script als administrator.'
}
if (-not (Test-Path $WinSw)) { throw "WinSW niet gevonden: $WinSw" }
if (-not $Java) { $Java = (Get-Command java.exe -ErrorAction Stop).Source }
if (-not (Test-Path $Java)) { throw "java.exe niet gevonden: $Java" }
if ($Account -and -not $Wachtwoord) { throw 'Geef bij -Account ook -Wachtwoord op.' }

$dienst = 'hkh-workflow'
$serviceMap = Join-Path $Map 'service'
$exe = Join-Path $serviceMap 'hkh-workflow.exe'
foreach ($d in 'app', 'updates', 'logs', 'service', 'config') { New-Item -ItemType Directory -Force (Join-Path $Map $d) | Out-Null }

# Een bestaande service eerst stoppen en weghalen, anders blijven exe en jar vergrendeld.
if (Get-Service $dienst -ErrorAction SilentlyContinue) {
    Write-Host 'Bestaande service wordt gestopt en verwijderd.'
    & $exe stop 2>&1 | Out-Null
    & $exe uninstall 2>&1 | Out-Null
    Start-Sleep -Seconds 2
}

Copy-Item -Force $WinSw $exe
Copy-Item -Force (Join-Path $hier 'run.ps1') (Join-Path $Map 'app\run.ps1')
$xml = (Get-Content -Raw (Join-Path $hier 'hkh-workflow.xml')) -replace '@@JAVA@@', $Java -replace '@@POORT@@', $Poort
[IO.File]::WriteAllText((Join-Path $serviceMap 'hkh-workflow.xml'), $xml, (New-Object Text.UTF8Encoding($false)))

if ($Jar) {
    Copy-Item -Force $Jar (Join-Path $Map 'app\current.jar')
    Write-Host "Jar geplaatst: $Map\app\current.jar"
} elseif (-not (Test-Path (Join-Path $Map 'app\current.jar'))) {
    Write-Host 'Let op: er is nog geen app\current.jar; zet een hkh-workflow.jar in updates\ of draai opnieuw met -Jar.'
}

# Alleen het lokale netwerk mag bij de app.
$regel = "hkh-workflow ($Poort)"
if (-not (Get-NetFirewallRule -DisplayName $regel -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName $regel -Direction Inbound -Protocol TCP -LocalPort $Poort -Action Allow -Profile Any -RemoteAddress LocalSubnet | Out-Null
    Write-Host "Firewallregel aangemaakt: $regel (alleen lokaal netwerk)"
}

& $exe install
if ($LASTEXITCODE -ne 0) { throw "Installeren mislukt (code $LASTEXITCODE)." }
if ($Account) {
    # Via sc.exe, zodat het wachtwoord niet leesbaar in het xml-bestand staat. Het account heeft het recht "Aanmelden als service" nodig.
    & sc.exe config $dienst obj= $Account password= $Wachtwoord | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Account instellen mislukt (code $LASTEXITCODE)." }
}
Write-Host ('Service geinstalleerd, account: ' + $(if ($Account) { $Account } else { 'LocalSystem' }))

if (-not $NietStarten) {
    & $exe start
    for ($i = 0; $i -lt 60; $i++) {
        try { if ((Invoke-WebRequest -Uri "http://localhost:$Poort/actuator/health" -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200) { Write-Host "De app draait op poort $Poort."; return } } catch { }
        Start-Sleep -Seconds 2
    }
    Write-Host "De app antwoordt nog niet op poort $Poort; kijk in $Map\logs\update.log en app-fout.log."
    exit 1
}
