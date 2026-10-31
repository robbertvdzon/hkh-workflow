# Als administrator uitvoeren. Zet alles terug wat voor de test is aangepast (SSH laat je los met setup-ssh.ps1 -Verwijder).
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$hosts = "$env:SystemRoot\System32\drivers\etc\hosts"
Set-Content -Path $hosts -Value @(Get-Content $hosts | Where-Object { $_ -notmatch '# images-test' }) -Encoding ascii
Get-NetFirewallRule -DisplayName 'images-test blok*' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
Set-DnsClientServerAddress -InterfaceAlias 'Wi-Fi' -ResetServerAddresses
Clear-DnsClientCache
'hosts-regels, firewallregels en DNS-instelling teruggezet.'
