# Installeert hkh-workflow als Windows-service (met WinSW) en start hem. Draai dit als administrator.
# Opnieuw draaien mag: een bestaande service wordt eerst gestopt en opnieuw geinstalleerd; instellingen, logs en updates blijven staan.
#
#   .\install.ps1                                  # WinSW.exe en hkh-workflow.jar naast dit script; de service draait als LocalSystem (het
#                                                  # systeemaccount van Windows), dus onafhankelijk van een gebruiker en diens wachtwoord
#   .\install.ps1 -Account '.\hkhservice'          # onder een eigen account (nodig voor netwerkmappen); vraagt om het wachtwoord
#   .\install.ps1 -GeenVragen                      # zonder vragen (zo doet deploy-testlaptop.sh het)
#   .\install.ps1 -WinSw C:\pad\WinSW.exe -Jar C:\pad\hkh-workflow.jar
#
# Alleen ASCII in dit bestand: Windows PowerShell 5.1 leest bestanden zonder BOM als ANSI.

param(
    [string]$WinSw,
    [string]$Jar,
    [string]$Map = 'C:\zcbs-workflow',
    [int]$Poort = 9000,
    [string]$Java,
    [string]$Account,
    [string]$Wachtwoord,
    [switch]$LocalSystem,
    [switch]$GeenVragen,
    [switch]$NietStarten
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$hier = Split-Path -Parent $MyInvocation.MyCommand.Path

function Lees-Geheim([string]$vraag) {
    $veilig = Read-Host -Prompt $vraag -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($veilig)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

# Klopt dit Windows-wachtwoord? Geeft $null als het niet te controleren is (dan merkt de start van de service het).
function Test-Wachtwoord([string]$domein, [string]$gebruiker, [string]$wachtwoord) {
    try {
        Add-Type -AssemblyName System.DirectoryServices.AccountManagement
        if ($domein -eq $env:COMPUTERNAME) { $ctx = New-Object System.DirectoryServices.AccountManagement.PrincipalContext('Machine', $env:COMPUTERNAME) }
        else { $ctx = New-Object System.DirectoryServices.AccountManagement.PrincipalContext('Domain', $domein) }
        try { return $ctx.ValidateCredentials($gebruiker, $wachtwoord) } finally { $ctx.Dispose() }
    } catch { return $null }
}

# Geeft het account het recht "Aanmelden als service"; zonder dat recht start de service niet (fout 1069).
function Grant-ServiceLogon([string]$sid) {
    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Security.Principal;

public static class HkhLsa
{
    [StructLayout(LayoutKind.Sequential)]
    struct LSA_UNICODE_STRING { public ushort Length; public ushort MaximumLength; public IntPtr Buffer; }

    [StructLayout(LayoutKind.Sequential)]
    struct LSA_OBJECT_ATTRIBUTES
    {
        public int Length; public IntPtr RootDirectory; public IntPtr ObjectName; public uint Attributes;
        public IntPtr SecurityDescriptor; public IntPtr SecurityQualityOfService;
    }

    [DllImport("advapi32.dll")]
    static extern uint LsaOpenPolicy(IntPtr systemName, ref LSA_OBJECT_ATTRIBUTES attributes, uint access, out IntPtr handle);

    [DllImport("advapi32.dll")]
    static extern uint LsaAddAccountRights(IntPtr handle, byte[] sid, LSA_UNICODE_STRING[] rights, uint count);

    [DllImport("advapi32.dll")]
    static extern uint LsaClose(IntPtr handle);

    [DllImport("advapi32.dll")]
    static extern int LsaNtStatusToWinError(uint status);

    public static void Grant(string sidText, string right)
    {
        SecurityIdentifier sid = new SecurityIdentifier(sidText);
        byte[] bytes = new byte[sid.BinaryLength];
        sid.GetBinaryForm(bytes, 0);
        LSA_OBJECT_ATTRIBUTES attributes = new LSA_OBJECT_ATTRIBUTES();
        attributes.Length = Marshal.SizeOf(typeof(LSA_OBJECT_ATTRIBUTES));
        IntPtr handle;
        // 0x810 = POLICY_CREATE_ACCOUNT | POLICY_LOOKUP_NAMES
        uint status = LsaOpenPolicy(IntPtr.Zero, ref attributes, 0x810, out handle);
        if (status != 0) throw new Win32Exception(LsaNtStatusToWinError(status));
        try
        {
            LSA_UNICODE_STRING[] rights = new LSA_UNICODE_STRING[1];
            rights[0].Buffer = Marshal.StringToHGlobalUni(right);
            rights[0].Length = (ushort)(right.Length * 2);
            rights[0].MaximumLength = (ushort)((right.Length + 1) * 2);
            try { status = LsaAddAccountRights(handle, bytes, rights, 1); }
            finally { Marshal.FreeHGlobal(rights[0].Buffer); }
            if (status != 0) throw new Win32Exception(LsaNtStatusToWinError(status));
        }
        finally { LsaClose(handle); }
    }
}
'@
    [HkhLsa]::Grant($sid, 'SeServiceLogonRight')
}

if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Draai dit script als administrator (rechtsklik op installeren.cmd, Als administrator uitvoeren).'
}
if (-not $WinSw) { $WinSw = Join-Path $hier 'WinSW.exe' }
if (-not (Test-Path $WinSw)) { throw "WinSW niet gevonden: $WinSw" }
if (-not $Jar -and (Test-Path (Join-Path $hier 'hkh-workflow.jar'))) { $Jar = Join-Path $hier 'hkh-workflow.jar' }
if ($Jar -and -not (Test-Path $Jar)) { throw "Jar niet gevonden: $Jar" }
if (-not $Java) {
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $cmd) { throw 'java.exe niet gevonden. Installeer Java 21 of nieuwer (Temurin JRE van adoptium.net, met "Add to PATH"), open een nieuw venster en probeer opnieuw; of geef het pad mee met -Java.' }
    $Java = $cmd.Source
}
if (-not (Test-Path $Java)) { throw "java.exe niet gevonden: $Java" }
$javaVersie = (Get-Item $Java).VersionInfo.ProductMajorPart
if ($javaVersie -gt 0 -and $javaVersie -lt 21) { Write-Host "Let op: $Java lijkt Java $javaVersie te zijn; de app heeft Java 21 of nieuwer nodig." }
if ($LocalSystem -and $Account) { throw 'Geef -LocalSystem of -Account op, niet allebei.' }

# Onder welk account draait de service? Standaard LocalSystem; alleen met -Account een gewoon account (-LocalSystem bestaat nog voor oudere aanroepen).
$accountSid = $null
if ($Account) {
    if ($Account -notmatch '\\') { $Account = "$env:COMPUTERNAME\$Account" }
    $domein, $gebruiker = $Account -split '\\', 2
    if ($domein -eq '.') { $domein = $env:COMPUTERNAME }
    try { $accountSid = (New-Object Security.Principal.NTAccount($domein, $gebruiker)).Translate([Security.Principal.SecurityIdentifier]).Value }
    catch { throw "Het account $domein\$gebruiker bestaat niet." }
    # Een lokaal account schrijft Windows als .\naam
    $Account = if ($domein -eq $env:COMPUTERNAME) { ".\$gebruiker" } else { "$domein\$gebruiker" }

    Write-Host "De service gaat draaien als $domein\$gebruiker."
    $gevraagd = -not $Wachtwoord
    for ($poging = 1; ; $poging++) {
        if ($gevraagd) { $Wachtwoord = Lees-Geheim "Windows-wachtwoord van $domein\$gebruiker" }
        if (-not $Wachtwoord) { throw 'Een account zonder wachtwoord kan geen service draaien. Geef het account een wachtwoord, of laat -Account weg (LocalSystem).' }
        $klopt = Test-Wachtwoord $domein $gebruiker $Wachtwoord
        if ($klopt -ne $false) { break }
        if (-not $gevraagd -or $poging -ge 3) { throw "Het wachtwoord van $domein\$gebruiker klopt niet." }
        Write-Host 'Dat wachtwoord klopt niet; probeer het opnieuw.'
    }
    if ($null -eq $klopt) { Write-Host 'Het wachtwoord kon niet vooraf gecontroleerd worden; dat blijkt bij het starten van de service.' }
} else {
    Write-Host 'De service gaat draaien als LocalSystem (het systeemaccount van Windows).'
}

$dienst = 'hkh-workflow'
$serviceMap = Join-Path $Map 'service'
$exe = Join-Path $serviceMap 'hkh-workflow.exe'
foreach ($d in 'app', 'updates', 'logs', 'service', 'config') { New-Item -ItemType Directory -Force (Join-Path $Map $d) | Out-Null }

# Het beheerderswachtwoord van de app (voor Instellingen, Sessies, Versie en Logs). Zonder eigen wachtwoord geldt het voorbeeld uit de jar.
$config = Join-Path $Map 'config\application.properties'
$heeftBeheer = (Test-Path $config) -and [bool](Select-String -Path $config -Pattern '^\s*hkh\.workflow\.admin-password\s*=' -Quiet)
if (-not $heeftBeheer -and -not $env:HKH_WORKFLOW_ADMIN_PASSWORD) {
    if ($GeenVragen) {
        Write-Host "Let op: er is geen eigen beheerderswachtwoord ingesteld (hkh.workflow.admin-password in $config)."
    } else {
        Write-Host 'Kies een beheerderswachtwoord voor de app (voor de pagina Instellingen). Alleen letters, cijfers en gewone leestekens.'
        while ($true) {
            $beheer = Lees-Geheim 'Beheerderswachtwoord van de app'
            if (-not $beheer) { Write-Host 'Leeg wachtwoord: er is nu geen eigen beheerderswachtwoord ingesteld.'; break }
            if ($beheer -match '[^\x21-\x7E]') { Write-Host 'Gebruik alleen letters, cijfers en gewone leestekens, zonder spaties.'; continue }
            if ($beheer -ne (Lees-Geheim 'Nog een keer')) { Write-Host 'De twee wachtwoorden zijn niet gelijk.'; continue }
            # In een properties-bestand is \ een speciaal teken. ASCII, want een BOM vooraan maakt de eerste regel onleesbaar.
            Add-Content -Path $config -Encoding ASCII -Value ('hkh.workflow.admin-password=' + $beheer.Replace('\', '\\'))
            Write-Host "Beheerderswachtwoord opgeslagen in $config"
            break
        }
    }
}

# Een bestaande service eerst stoppen en weghalen, anders blijven exe en jar vergrendeld.
if (Get-Service $dienst -ErrorAction SilentlyContinue) {
    Write-Host 'Bestaande service wordt gestopt en verwijderd.'
    if (Test-Path $exe) {
        & $exe stop 2>&1 | Out-Null
        & $exe uninstall 2>&1 | Out-Null
    } else {
        Stop-Service $dienst -Force -ErrorAction SilentlyContinue
        & sc.exe delete $dienst | Out-Null
    }
    # Wachten tot WinSW en java echt weg zijn; anders zijn hun bestanden nog vergrendeld.
    for ($i = 0; $i -lt 30; $i++) {
        $nog = @(Get-Process hkh-workflow -ErrorAction SilentlyContinue) + @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*current.jar*' })
        if ($nog.Count -eq 0) { break }
        if ($i -eq 15) { Get-Process hkh-workflow -ErrorAction SilentlyContinue | Stop-Process -Force; Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*current.jar*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } }
        Start-Sleep -Seconds 1
    }
}

Copy-Item -Force $WinSw $exe
Copy-Item -Force (Join-Path $hier 'run.ps1') (Join-Path $Map 'app\run.ps1')
$xml = (Get-Content -Raw (Join-Path $hier 'hkh-workflow.xml')) -replace '@@JAVA@@', $Java -replace '@@POORT@@', $Poort
[IO.File]::WriteAllText((Join-Path $serviceMap 'hkh-workflow.xml'), $xml, (New-Object Text.UTF8Encoding($false)))

if ($Jar) {
    Copy-Item -Force $Jar (Join-Path $Map 'app\current.jar')
    # Een klaarstaande update van eerder zou deze jar bij de volgende start weer overschrijven (updates\ gaat voor), dus die vervalt.
    Remove-Item -Force (Join-Path $Map 'updates\hkh-workflow.jar*') -ErrorAction SilentlyContinue
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
if ($accountSid) {
    Grant-ServiceLogon $accountSid
    # Via Windows zelf en niet in het xml-bestand, zodat het wachtwoord nergens leesbaar op schijf staat.
    $uitkomst = Get-CimInstance Win32_Service -Filter "Name='$dienst'" | Invoke-CimMethod -MethodName Change -Arguments @{ StartName = $Account; StartPassword = $Wachtwoord }
    if ($uitkomst.ReturnValue -ne 0) { throw "Account instellen mislukt (code $($uitkomst.ReturnValue))." }
}
Write-Host ('Service geinstalleerd, account: ' + $(if ($accountSid) { $Account } else { 'LocalSystem' }))

if (-not $NietStarten) {
    & $exe start
    for ($i = 0; $i -lt 60; $i++) {
        try { if ((Invoke-WebRequest -Uri "http://localhost:$Poort/actuator/health" -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200) { Write-Host "De app draait: http://localhost:$Poort  (vanaf een andere pc: http://${env:COMPUTERNAME}:$Poort)"; return } } catch { }
        Start-Sleep -Seconds 2
    }
    Write-Host "De app antwoordt nog niet op poort $Poort; kijk in $Map\logs\update.log en app-fout.log."
    exit 1
}
