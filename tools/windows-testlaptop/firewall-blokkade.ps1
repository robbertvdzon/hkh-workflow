# Als administrator uitvoeren. Blokkeert alle uitgaande verbindingen van images.exe en i_view32.exe, behalve naar de Mac
# met de test-ZCBS. Vangnet voor als een hostnaam of een vast IP-adres toch naar de echte server wijst.
# De regels gelden per programmapad: draai images.exe dus alleen vanuit $Map.
param([string]$Mac = '192.168.178.144', [string]$Map = 'C:\images-test')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function NaarGetal([string]$ip) { $b = $ip.Split('.') | ForEach-Object { [uint64]$_ }; return ($b[0] -shl 24) + ($b[1] -shl 16) + ($b[2] -shl 8) + $b[3] }
function NaarIp([uint64]$n) { return ((($n -shr 24) -band 255), (($n -shr 16) -band 255), (($n -shr 8) -band 255), ($n -band 255)) -join '.' }

$n = NaarGetal $Mac
$v4 = @(('0.0.0.0-' + (NaarIp ($n - 1))), ((NaarIp ($n + 1)) + '-255.255.255.255'))
$v6 = @('::/1', '8000::/1')    # Windows accepteert '::/0' niet

Get-NetFirewallRule -DisplayName 'images-test blok*' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
foreach ($exe in 'images.exe', 'i_view32.exe') {
    $pad = Join-Path $Map $exe
    New-NetFirewallRule -DisplayName "images-test blok v4 $exe" -Direction Outbound -Program $pad -Action Block -RemoteAddress $v4 -Profile Any | Out-Null
    New-NetFirewallRule -DisplayName "images-test blok v6 $exe" -Direction Outbound -Program $pad -Action Block -RemoteAddress $v6 -Profile Any | Out-Null
}
Get-NetFirewallRule -DisplayName 'images-test blok*' | Sort-Object DisplayName | Format-Table DisplayName, Enabled, Action, Direction -AutoSize
