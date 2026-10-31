# Zet op deze Windows-testlaptop een SSH-server aan, alleen bereikbaar met sleutel vanaf het eigen netwerk.
# Uitvoeren in een PowerShell die "als administrator" is gestart. De gebruiker waaronder je dit doet
# wordt de SSH-gebruiker (en moet beheerder zijn).
#
# Opruimen als je klaar bent: .\setup-ssh.ps1 -Verwijder

param([switch]$Verwijder)

$ErrorActionPreference = 'Stop'
$publicKey = 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJklZ/66cHrRAnYqdLyLSSEgHhN7vsmY0wlNiY+zvogd claude-testlaptop'
$authKeys  = "$env:ProgramData\ssh\administrators_authorized_keys"
$config    = "$env:ProgramData\ssh\sshd_config"

if ($Verwijder) {
    Stop-Service sshd -ErrorAction SilentlyContinue
    Set-Service sshd -StartupType Disabled -ErrorAction SilentlyContinue
    Remove-Item $authKeys -ErrorAction SilentlyContinue
    Disable-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -ErrorAction SilentlyContinue
    Write-Host 'SSH-server uitgezet en sleutel verwijderd.'
    return
}

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { throw 'Start PowerShell als administrator.' }

# 1. OpenSSH Server installeren (vraagt internet / Windows Update)
if (-not (Get-WindowsCapability -Online -Name 'OpenSSH.Server*' | Where-Object State -eq 'Installed')) {
    Add-WindowsCapability -Online -Name 'OpenSSH.Server~~~~0.0.1.0' | Out-Null
}

# 2. Eerst de service één keer starten, dan bestaan de standaardbestanden (sshd_config)
Set-Service sshd -StartupType Automatic
Start-Service sshd
Stop-Service sshd

# 3. Alleen sleutel-login. Beheerders gebruiken administrators_authorized_keys met strikte rechten.
Set-Content -Path $authKeys -Value $publicKey -Encoding ascii
icacls.exe $authKeys /inheritance:r /grant 'Administrators:F' /grant 'SYSTEM:F' | Out-Null

$tekst = Get-Content $config -Raw
$tekst = $tekst -replace '(?m)^\s*#?\s*PasswordAuthentication\s+.*$', 'PasswordAuthentication no'
$tekst = $tekst -replace '(?m)^\s*#?\s*PubkeyAuthentication\s+.*$', 'PubkeyAuthentication yes'
Set-Content -Path $config -Value $tekst -Encoding ascii

# 4. PowerShell als standaardshell
New-ItemProperty -Path 'HKLM:\SOFTWARE\OpenSSH' -Name DefaultShell -Value 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe' -PropertyType String -Force | Out-Null

# 5. Firewall: poort 22 alleen vanaf het lokale netwerk, ook op "openbare" netwerken
$regel = Get-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -ErrorAction SilentlyContinue
if (-not $regel) {
    New-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -DisplayName 'OpenSSH Server (sshd)' -Enabled True -Direction Inbound -Protocol TCP -Action Allow -LocalPort 22 | Out-Null
}
Set-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -Enabled True -Profile Any -RemoteAddress LocalSubnet

Start-Service sshd

Write-Host ''
Write-Host 'Klaar. SSH luistert op poort 22 (alleen sleutel, alleen lokaal netwerk).'
Write-Host 'IP-adressen van deze laptop:'
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } | Format-Table InterfaceAlias, IPAddress -AutoSize
Write-Host "Gebruikersnaam voor SSH: $env:USERNAME"
