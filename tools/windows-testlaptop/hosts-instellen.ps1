# Als administrator uitvoeren. Stuurt de twee namen die images.exe gebruikt naar de Mac met de test-ZCBS.
# Je hebt dan geen eigen DNS-server nodig; de rest van het internet blijft gewoon werken.
param([string]$Mac = '192.168.178.144')
$ErrorActionPreference = 'Stop'
$hosts = "$env:SystemRoot\System32\drivers\etc\hosts"
$regels = @(Get-Content $hosts | Where-Object { $_ -notmatch '# images-test' })
$regels += "$Mac`tftp.historischekringheemskerk.nl`t# images-test"
$regels += "$Mac`twww.zcbs.nl`t# images-test"
Set-Content -Path $hosts -Value $regels -Encoding ascii
Clear-DnsClientCache
foreach ($naam in 'ftp.historischekringheemskerk.nl', 'www.zcbs.nl') {
    $ip = [Net.Dns]::GetHostAddresses($naam) | Where-Object AddressFamily -eq 'InterNetwork' | ForEach-Object { $_.IPAddressToString }
    "$naam -> $ip"
}
