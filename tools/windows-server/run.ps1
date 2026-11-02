# Kern van de Windows-service: draait de app in een lus en regelt het bijwerken en terugdraaien.
# De service (WinSW) start dit script; het script start java. Zie tools/windows-server/README.md en docs/auto-update.md.
#
# Mapindeling (relatief aan de map waar dit script in app\ staat):
#   app\current.jar       de draaiende versie            app\previous.jar   de versie daarvoor (om terug te draaien)
#   updates\              hier komt een nieuwe hkh-workflow.jar (en eventueel hkh-workflow.jar.sha256) te staan
#   updates\mislukt\      jars die niet startten of niet gezond werden
#   logs\update.log       wat dit script doet; logs\app-uit.log en app-fout.log zijn de uitvoer van java
#
# Regels van de lus:
#   1. Staat er een jar in updates\? Controleren (zip, sha256) en voor current.jar zetten; de oude wordt previous.jar.
#   2. java -jar current.jar starten en wachten tot /actuator/health antwoordt (maximaal $HealthSeconden).
#   3. Eindigt java met exitcode 10: de app vroeg om een update, dus opnieuw bij stap 1.
#      Eindigt java met 0: gewone stop, het script eindigt (de service stopt).
#      Iets anders (crash): als de jar net nieuw was en nooit gezond is geworden, dan terugdraaien naar previous.jar;
#      anders na een korte pauze opnieuw starten.
# Alleen ASCII in dit bestand: Windows PowerShell 5.1 leest bestanden zonder BOM als ANSI.

param(
    [string]$Map = (Split-Path -Parent $PSScriptRoot),
    [int]$Poort = 9000,
    [int]$HealthSeconden = 90
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if ($env:HKH_WORKFLOW_PORT) { $Poort = [int]$env:HKH_WORKFLOW_PORT }

$app = Join-Path $Map 'app'
$updates = Join-Path $Map 'updates'
$mislukt = Join-Path $updates 'mislukt'
$logs = Join-Path $Map 'logs'
$current = Join-Path $app 'current.jar'
$previous = Join-Path $app 'previous.jar'
$kandidaat = Join-Path $updates 'hkh-workflow.jar'
foreach ($d in @($app, $updates, $mislukt, $logs)) { New-Item -ItemType Directory -Force $d | Out-Null }

function Log([string]$tekst) {
    $regel = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss') + '  ' + $tekst
    Add-Content -Path (Join-Path $logs 'update.log') -Value $regel
    Write-Host $regel
}

function Get-Java {
    if ($env:HKH_JAVA -and (Test-Path $env:HKH_JAVA)) { return $env:HKH_JAVA }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { return (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw 'java.exe niet gevonden (zet HKH_JAVA of JAVA_HOME).'
}

# Is dit een leesbare jar (zip met een manifest)? Vangt een half gekopieerd of beschadigd bestand af.
function Test-Jar([string]$pad) {
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [IO.Compression.ZipFile]::OpenRead($pad)
        try { return ($zip.Entries | Where-Object { $_.FullName -eq 'META-INF/MANIFEST.MF' }).Count -gt 0 } finally { $zip.Dispose() }
    } catch { return $false }
}

function Move-NaarMislukt([string]$pad, [string]$reden) {
    $naam = 'hkh-workflow-' + (Get-Date).ToString('yyyyMMdd-HHmmss') + '.jar'
    Move-Item -Force $pad (Join-Path $mislukt $naam)
    Log ("Jar naar updates\mislukt\$naam : $reden")
}

# Zet een klaarstaande jar uit updates\ als current.jar. Geeft $true als er een nieuwe jar is geplaatst.
function Install-Update {
    if (-not (Test-Path $kandidaat)) { return $false }
    $sha = $kandidaat + '.sha256'
    if (Test-Path $sha) {
        $verwacht = ((Get-Content $sha -TotalCount 1) -split '\s+')[0].ToLower()
        $echt = (Get-FileHash -Algorithm SHA256 $kandidaat).Hash.ToLower()
        if ($verwacht -ne $echt) { Move-NaarMislukt $kandidaat 'sha256 klopt niet'; Remove-Item -Force $sha; return $false }
        Remove-Item -Force $sha
    }
    if (-not (Test-Jar $kandidaat)) { Move-NaarMislukt $kandidaat 'geen geldige jar'; return $false }
    if (Test-Path $current) { Move-Item -Force $current $previous }
    Move-Item -Force $kandidaat $current
    Log ('Nieuwe versie geplaatst (' + [math]::Round((Get-Item $current).Length / 1MB, 1) + ' MB)')
    return $true
}

function Test-Health {
    try { return (Invoke-WebRequest -Uri "http://localhost:$Poort/actuator/health" -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 }
    catch { return $false }
}

# Houdt hooguit twee oude uitvoerlogs bij; java overschrijft de huidige bij elke start.
function Rotate-Log([string]$pad) {
    if (Test-Path $pad) { Move-Item -Force $pad ($pad + '.1') }
}

Log ("Service gestart. Map=$Map, poort=$Poort")
$java = Get-Java
Log "Java: $java"
$nieuw = $false

while ($true) {
    if (Install-Update) { $nieuw = $true }
    if (-not (Test-Path $current)) {
        Log 'Geen app\current.jar en niets in updates\; over 30 seconden opnieuw kijken.'
        Start-Sleep -Seconds 30
        continue
    }

    $uit = Join-Path $logs 'app-uit.log'
    $fout = Join-Path $logs 'app-fout.log'
    Rotate-Log $uit
    Rotate-Log $fout
    $proces = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $current + '"')) -WorkingDirectory $Map `
        -NoNewWindow -PassThru -RedirectStandardOutput $uit -RedirectStandardError $fout
    $null = $proces.Handle   # zonder dit blijft ExitCode soms leeg
    Log ("java gestart (pid $($proces.Id))" + $(if ($nieuw) { ', nieuwe versie' } else { '' }))

    $start = Get-Date
    $gezond = $false
    while (-not $proces.HasExited) {
        Start-Sleep -Seconds 2
        if (-not $gezond) {
            if (Test-Health) {
                $gezond = $true
                Log ('App antwoordt na ' + [int]((Get-Date) - $start).TotalSeconds + ' seconden')
            } elseif (((Get-Date) - $start).TotalSeconds -gt $HealthSeconden) {
                Log "App antwoordt niet binnen $HealthSeconden seconden; wordt gestopt."
                & taskkill.exe /PID $proces.Id /T /F | Out-Null
                break
            }
        }
    }
    $proces.WaitForExit()
    $code = $proces.ExitCode
    Log "java is gestopt met exitcode $code"

    if ($code -eq 10) { Log 'De app vraagt om een update.'; continue }
    if ($code -eq 0 -and $gezond) { Log 'Gewone stop; de service eindigt.'; exit 0 }

    if ($nieuw -and -not $gezond -and (Test-Path $previous)) {
        Move-NaarMislukt $current 'startte niet of werd niet gezond'
        Copy-Item -Force $previous $current
        Log 'Teruggedraaid naar de vorige versie.'
        $nieuw = $false
        continue
    }
    if ($gezond) { $nieuw = $false }
    Log 'Wacht 10 seconden en start opnieuw.'
    Start-Sleep -Seconds 10
}
