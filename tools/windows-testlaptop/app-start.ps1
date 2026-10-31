# Draait op de Windows-testlaptop. Stopt hkh-workflow.jar als die draait en start hem opnieuw (of alleen stoppen met -Stop).
# Wordt aangeroepen door deploy-app.sh op de Mac; los gebruiken kan ook:
#   powershell -NoProfile -ExecutionPolicy Bypass -File C:\zcbs-workflow\app-start.ps1 [-Stop] [-Poort 9000]
#
# De app draait los van de SSH-sessie en schrijft naar app.log / app.err naast de jar.

param(
    [switch]$Stop,
    [int]$Poort = 9000,
    [string]$Map = 'C:\zcbs-workflow'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$jar = Join-Path $Map 'hkh-workflow.jar'

function Get-AppProcessen {
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -like '*hkh-workflow.jar*' }
}

# 1. Stoppen wat er draait
$lopend = @(Get-AppProcessen)
if ($lopend.Count -gt 0) {
    foreach ($p in $lopend) { Stop-Process -Id $p.ProcessId -Force }
    for ($i = 0; $i -lt 20 -and @(Get-AppProcessen).Count -gt 0; $i++) { Start-Sleep -Milliseconds 500 }
    if (@(Get-AppProcessen).Count -gt 0) { throw 'De app wil niet stoppen.' }
    Write-Host ('Gestopt: pid ' + (($lopend | ForEach-Object { $_.ProcessId }) -join ', '))
} else {
    Write-Host 'De app draaide niet.'
}
if ($Stop) { return }

if (-not (Test-Path $jar)) { throw "Jar niet gevonden: $jar" }

# 2. Alleen het lokale netwerk mag erbij (poort van de app); regel eenmalig aanmaken
$regelNaam = "hkh-workflow ($Poort)"
if (-not (Get-NetFirewallRule -DisplayName $regelNaam -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName $regelNaam -Direction Inbound -Protocol TCP -LocalPort $Poort `
        -Action Allow -Profile Any -RemoteAddress LocalSubnet | Out-Null
    Write-Host "Firewallregel aangemaakt: $regelNaam (alleen lokaal netwerk)"
}

# 3. Starten, los van deze sessie. Start-Process zou het proces aan de SSH-sessie hangen en hem bij het
#    afsluiten van de sessie beëindigen; een proces via WMI heeft die koppeling niet.
$java = (Get-Command java -ErrorAction Stop).Source
$log = Join-Path $Map 'app.log'
$err = Join-Path $Map 'app.err'
$opdracht = "cmd.exe /c `"set HKH_WORKFLOW_PORT=$Poort&& `"$java`" -jar `"$jar`" > `"$log`" 2> `"$err`"`""
$start = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $opdracht; CurrentDirectory = $Map }
if ($start.ReturnValue -ne 0) { throw "Starten mislukt (WMI-code $($start.ReturnValue))." }
Write-Host ('Gestart (pid van cmd: ' + $start.ProcessId + ')')

# 4. Wachten tot hij antwoordt
$klaar = $false
for ($i = 0; $i -lt 60; $i++) {
    if (-not (Get-Process -Id $start.ProcessId -ErrorAction SilentlyContinue)) { break }
    try {
        $antwoord = Invoke-WebRequest -Uri "http://localhost:$Poort/actuator/health" -UseBasicParsing -TimeoutSec 2
        if ($antwoord.StatusCode -eq 200) { $klaar = $true; break }
    } catch { }
    Start-Sleep -Seconds 1
}
if ($klaar) {
    Write-Host "De app draait op poort $Poort."
} else {
    Write-Host 'De app is niet gestart binnen 60 seconden. Laatste regels van app.err:'
    if (Test-Path (Join-Path $Map 'app.err')) { Get-Content (Join-Path $Map 'app.err') -Tail 15 }
    Write-Host 'Laatste regels van app.log:'
    if (Test-Path (Join-Path $Map 'app.log')) { Get-Content (Join-Path $Map 'app.log') -Tail 15 }
    exit 1
}
