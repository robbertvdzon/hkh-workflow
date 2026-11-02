# Alleen voor de testlaptop. Als administrator uitvoeren. De app draait images.exe en pdf2zcbs.exe vanuit kopieen in de werkmap
# (<werkmap>\<gebruiker>\<collectie>\images en \pdf2zcbs). Windows-firewallregels gelden per programmapad, dus de blokkade van
# tools/windows-testlaptop/firewall-blokkade.ps1 (C:\images-test) dekt die kopieen niet. Dit script blokkeert ze voor alles behalve de Mac
# met de test-ZCBS, als vangnet voor het geval de hosts-omleiding ooit wegvalt en een programma de echte HKH-server zou bereiken.
#   .\firewall-werkmap.ps1 -Werkmap C:\zcbs-werk -Gebruikers user1 -Mac 192.168.178.144
# Alleen ASCII in dit bestand (Windows PowerShell 5.1).
param(
    [string]$Werkmap = 'C:\zcbs-werk',
    [string[]]$Gebruikers = @('user1'),
    [string]$Collectie = 'archief',
    [string]$Mac = '192.168.178.144'
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function NaarGetal([string]$ip) { $b = $ip.Split('.') | ForEach-Object { [uint64]$_ }; return ($b[0] -shl 24) + ($b[1] -shl 16) + ($b[2] -shl 8) + $b[3] }
function NaarIp([uint64]$n) { return ((($n -shr 24) -band 255), (($n -shr 16) -band 255), (($n -shr 8) -band 255), ($n -band 255)) -join '.' }

$n = NaarGetal $Mac
$v4 = @(('0.0.0.0-' + (NaarIp ($n - 1))), ((NaarIp ($n + 1)) + '-255.255.255.255'))
$v6 = @('::/1', '8000::/1')    # Windows accepteert '::/0' niet

Get-NetFirewallRule -DisplayName 'hkh-workflow blok*' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
foreach ($gebruiker in $Gebruikers) {
    $root = Join-Path (Join-Path $Werkmap $gebruiker) $Collectie
    foreach ($rel in 'images\images.exe', 'images\i_view32.exe', 'pdf2zcbs\pdf2zcbs.exe', 'pdf2zcbs\bin\i_view32.exe', 'pdf2zcbs\bin\pdftotext.exe', 'pdf2zcbs\bin\pdftopng.exe') {
        $pad = Join-Path $root $rel
        $naam = "$gebruiker/$Collectie/$rel"
        New-NetFirewallRule -DisplayName "hkh-workflow blok v4 $naam" -Direction Outbound -Program $pad -Action Block -RemoteAddress $v4 -Profile Any | Out-Null
        New-NetFirewallRule -DisplayName "hkh-workflow blok v6 $naam" -Direction Outbound -Program $pad -Action Block -RemoteAddress $v6 -Profile Any | Out-Null
    }
}
(Get-NetFirewallRule -DisplayName 'hkh-workflow blok*' | Measure-Object).Count.ToString() + ' blokkaderegels aangemaakt.'
