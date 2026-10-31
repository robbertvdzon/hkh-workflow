# Stuurt images.exe aan zoals de Kotlin-app: uitvoer lezen, bij een herkende vraag het antwoord + CRLF sturen.
param([int]$MaxSeconden = 120)
$enc = [Text.Encoding]::GetEncoding(850)
$regels = @(
  @('Type ENTER/RETURN indien OK', 'a'),
  @('Kies 1,2,3', '6'),
  @('Toch de afgeleide images', 'j'),
  @('UPLOADEN aangemaakte images', 'j'),
  @('Nummer, naam of plaats instelling', '4'),
  @('FTP wachtwoord', 'hkhtest'),
  @('Kies de ZCBS-toepassing', 'archief'),
  @('Geef een ENTER/RETURN', ''),
  @('OVERSCHRIJVEN', 'n'),
  @('AANMAKEN en UPLOADEN van de bijbehorende', 'j'),
  @('VERWIJDEREN (tijdelijk) aangemaakte mappen', 'n'),
  @('PC-bestand met nieuwe database records', 'n'),
  @('VERWIJDEREN inhoud van de map [images]', 'n'),
  @('om dit DOS-window te verlaten', '')
)
$psi = New-Object Diagnostics.ProcessStartInfo
$psi.FileName = 'C:\images-test\images.exe'
$psi.WorkingDirectory = 'C:\images-test'
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true; $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
$psi.StandardOutputEncoding = $enc; $psi.StandardErrorEncoding = $enc
$psi.CreateNoWindow = $true
$p = [Diagnostics.Process]::Start($psi)
$errTask = $p.StandardError.ReadToEndAsync()
$in = New-Object IO.StreamWriter($p.StandardInput.BaseStream, $enc)
$in.AutoFlush = $true
$stream = $p.StandardOutput.BaseStream
$buf = New-Object byte[] 4096
$read = $stream.ReadAsync($buf, 0, $buf.Length)
$all = New-Object Text.StringBuilder
$pending = ''
$start = Get-Date
$laatsteActie = Get-Date
$verzonden = @()
while ($true) {
  if (((Get-Date) - $start).TotalSeconds -gt $MaxSeconden) { $verzonden += '(TIJDLIMIET)'; break }
  if ($read.Wait(400)) {
    $n = $read.Result
    if ($n -le 0) { break }
    $t = $enc.GetString($buf, 0, $n)
    [void]$all.Append($t); $pending += $t
    $read = $stream.ReadAsync($buf, 0, $buf.Length)
    $laatsteActie = Get-Date
  } else {
    $norm = ($pending -replace '\x1b\[[0-9;]*m', '') -replace '\s+', ' '
    $beste = $null; $bestPos = -1
    foreach ($r in $regels) {
      $pos = $norm.LastIndexOf(($r[0] -replace '\s+', ' '), [StringComparison]::OrdinalIgnoreCase)
      if ($pos -gt $bestPos) { $bestPos = $pos; $beste = $r }
    }
    if ($beste) {
      $verzonden += ($beste[0] + ' => ' + $(if ($beste[0] -like 'FTP wachtwoord*') { '***' } else { $beste[1] }))
      $in.Write($beste[1] + "`r`n")
      $pending = ''
      $laatsteActie = Get-Date
    } elseif (((Get-Date) - $laatsteActie).TotalSeconds -gt 20) { $verzonden += '(VAST: geen herkende vraag)'; break }
    if ($p.HasExited -and $read.IsCompleted -eq $false) { Start-Sleep -Milliseconds 200 }
  }
}
if (-not $p.HasExited) { if (-not $p.WaitForExit(5000)) { Stop-Process -Id $p.Id -Force; $verzonden += '(gestopt)' } }
Write-Output ('exit: ' + $p.ExitCode)
Write-Output '=== antwoorden:'
$verzonden
Write-Output '=== uitvoer:'
($all.ToString() -replace '\x1b\[[0-9;]*m', '') -replace '\r', ''
Write-Output '=== stderr:'
$errTask.Result
