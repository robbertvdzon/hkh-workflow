#!/usr/bin/perl
# Nep-images.exe om de workflow op Mac/Linux te testen. Stelt dezelfde vragen als images.exe
# (zie docs/042.pdf, hoofdstuk 5). Zonder images-mock.ini uploadt het niets. Met images-mock.ini (zie
# images-mock.ini.example) doet het net als het echte programma een FTP-upload naar de (lokale) server
# en zet het de lege database records in updates.txt op de server. Net als een gewoon Perl-programma zonder
# $| = 1 buffert het zijn uitvoer als het niet in een terminal draait. Zet MOCK_UNBUFFERED=1 om
# dat uit te zetten.
use strict; use warnings; use File::Copy; use File::Path qw(make_path remove_tree); use FindBin qw($Bin); use Net::FTP; use File::Temp qw(tempfile);
$| = 1 if $ENV{MOCK_UNBUFFERED};
my %cfg;
if (open(my $ini, '<', "$Bin/images-mock.ini")) {
  while (<$ini>) { s/[\r\n]+$//; next if /^\s*(#|$)/; my ($k, $v) = split /\s*=\s*/, $_, 2; $cfg{lc $k} = defined $v ? $v : ''; }
  close $ini;
}
my $ftp_host = $cfg{ftp_host} || '';
my $ID1 = $cfg{id1} || 'HKH'; my $ID2 = $cfg{id2} || '997';
sub date3 { my @t = localtime; return sprintf('%02d%02d%02d-%02d:%02d', $t[5] % 100, $t[4] + 1, $t[3], $t[2], $t[1]); }
sub ask { my ($q) = @_; print $q; my $a = <STDIN>; $a = '' unless defined $a; $a =~ s/[\r\n]+$//; return $a; }

print "-" x 79, "\n Dit ZCBS-hulpprogramma (NEP-VERSIE) zorgt voor het:\n";
print " A. Inlezen en bepalen van de type images.\n B. Automatisch aanmaken van afgeleiden afbeeldingen (small/large/100pct).\n";
print " C. Uploaden via ftp van deze images naar de webserver.\n D. Aanmaken van de bijbehorende database records.\n";
print "-" x 79, "\n Versie MOCK -- Info: hkh-workflow\n", "-" x 79, "\n";

opendir(my $dh, "images") or die "Map images ontbreekt\n";
my @files = sort grep { /\.(jpe?g|tif|gif|png|bmp|pdf)$/i } readdir($dh); closedir($dh);
print " - Huidige map is: ", `pwd`;
print " Gevonden ", scalar(@files), " bestanden in map (\\images) van type: **** A ****\n";
my $t = ask(" > Type ENTER/RETURN indien OK -- of kies A, B of C -- of breek het programma af:\n");
print " - Resoluties voor aan te maken afgeleide afbeeldingen?\n   1 = small: 100x100 - large: 700x500 - 100pct: ongewijzigd\n   6 = small: 250x250 - large: 700x500 - 100pct: ongewijzigd\n";
my $r = ask(" Kies 1,2,3 (objecten,boeken,etc), of kies 6,7,8 (foto's,etc)\n");
# small = 100 of 250 (keuze 1-3 of 6-8), large = 700, 100pct = ongewijzigd. Met sips (macOS) echt verkleind, anders gekopieerd.
my %max = (small => ($r =~ /^[1-3]$/ ? 100 : 250), large => 700);
for my $d (qw(small large 100pct)) {
  make_path($d);
  for my $f (@files) {
    (my $j = $f) =~ s/\.[^.]+$/.jpg/;
    # Alleen verkleinen als de afbeelding groter is dan de doelmaat, zoals het echte programma (geen opschalen).
    my $side = 0;
    if ($max{$d} && $f =~ /\.jpe?g$/i) {
      my $dims = `sips -g pixelWidth -g pixelHeight "images/$f" 2>/dev/null`;
      my ($w) = $dims =~ /pixelWidth:\s*(\d+)/; my ($h) = $dims =~ /pixelHeight:\s*(\d+)/;
      $side = ($w && $h) ? ($w > $h ? $w : $h) : 0;
    }
    if ($side > ($max{$d} || 0) && system("sips -Z $max{$d} \"images/$f\" --out \"$d/$j\" >/dev/null 2>&1") == 0) { next; }
    copy("images/$f", "$d/$j");
  }
  print " - ", scalar(@files), " (van de ", scalar(@files), ") images via Irfanview omgezet en geplaatst in submap: \\$d\n";
  sleep 1;
}
my $up = ask(" > UPLOADEN aangemaakte images in small/large/100pct naar webserver? [J/N]\n");
my ($ftp, $app, $sub, $base, $uploaded) = (undef, '', '', $cfg{ftp_base} || 'domains/historischekringheemskerk.nl/public_html', 0);
if ($up =~ /^j/i) {
  my $inst;
  while (1) {
    $inst = ask(" > Nummer, naam of plaats instelling?\n");
    last if $inst =~ /^0*4$|heemskerk|historische/i;
    print " - ZCBS-instelling niet herkend. Geef een nummer, naam of plaats.\n";
  }
  print " > Welkom: ==== 004: Historische Kring Heemskerk ====\n";
  if ($ftp_host eq '') {
    ask(" > FTP wachtwoord van uw webserver?\n");
    print " > Beschikbare ZCBS-toepassingen:\n   - archief\n   - beeldbank\n   - test\n";
    $app = ask(" > Kies de ZCBS-toepassing (= tevens naam van de uploadmap op de webserver):\n");
    print " - NEP: niets geupload naar $app\n";
  } else {
    my $tries = 0;
    while (1) {
      my $pw = ask(" > FTP wachtwoord van uw webserver?\n");
      $ftp = Net::FTP->new($ftp_host, Port => ($cfg{ftp_port} || 21), Passive => 1, Timeout => 30);
      if ($ftp && $ftp->login($cfg{ftp_user} || 'hkhadmin', $pw)) { $ftp->binary; last; }
      print " - Het maken van een verbinding met de webserver is niet gelukt. Controleer het wachtwoord.\n";
      die "Te vaak een fout wachtwoord\n" if ++$tries >= 5;
    }
    $ftp->cwd($base) or die "Basismap $base niet gevonden op de server\n";
    my @apps = grep { defined $ftp->size("cgi-bin/$_/$_.txt") } map { (my $n = $_) =~ s{.*/}{}; $n } ($ftp->ls('cgi-bin'));
    print " > Beschikbare ZCBS-toepassingen:\n", map { "   - $_\n" } sort @apps;
    while (1) {
      $app = ask(" > Kies de ZCBS-toepassing (= tevens naam van de uploadmap op de webserver):\n");
      last if grep { $_ eq $app } @apps;
      print " - Onbekende toepassing: $app\n";
    }
    # Submappen: alleen als er in /small al mappen staan (zoals bij het echte programma).
    my @dirs = grep { /^d/ } $ftp->dir("$app/small");
    if (@dirs) {
      print " - de webserver map ==/public_html/$app/== bevat:\n   0. : de root\n";
      my $k = 1; my @names;
      for my $d (@dirs) { my ($n) = $d =~ /\s(\S+)\s*$/; push @names, $n; print "   $k. : $n\n"; $k++; }
      my $c = ask(" > Waar wilt u de images geuploaded hebben (kies 0-" . scalar(@names) . ")?\n   ==of==\n   Images naar een nieuwe submap? ==Geef dan alleen een enter/return==\n");
      if ($c =~ /^\d+$/ && $c > 0 && $c <= @names) { $sub = "$names[$c-1]/"; }
      elsif ($c eq '') { my $n = ask(" > Geef de naam van de nieuwe submap\n"); $ftp->mkdir("$app/$_/$n") for qw(small large); $ftp->mkdir("cgi-bin/$app/100pct/$n"); $sub = "$n/"; }
    }
    my %target = (small => "$app/small/$sub", large => "$app/large/$sub", '100pct' => "cgi-bin/$app/100pct/$sub");
    my @jpg = map { (my $j = $_) =~ s/\.[^.]+$/.jpg/; $j } @files;
    my $exist = 0;
    for my $d (qw(small large 100pct)) { for my $j (@jpg) { $exist++ if defined $ftp->size("$target{$d}$j"); } }
    my $over = 'j';
    if ($exist) { $over = ask(" > Oude images (met dezelfde naam) in small/large/100pct op webserver OVERSCHRIJVEN? [J/N]\n"); }
    for my $d (qw(small large 100pct)) {
      my $n = 0;
      for my $j (@jpg) {
        next if defined $ftp->size("$target{$d}$j") && $over !~ /^j/i;
        $n++ if $ftp->put("$d/$j", "$target{$d}$j");
      }
      print " - $n (van de ", scalar(@jpg), ") images via FTP geplaatst op webserver in de map /$d", ($sub ne '' ? "/$sub" : ''), "\n";
    }
    $uploaded = 1;
  }
}
# Ook na een nee op uploaden wordt naar de records gevraagd (zo staat het in het log van het echte programma).
my $db = ask(" > AANMAKEN en UPLOADEN van de bijbehorende " . scalar(@files) . " nieuwe lege DATABASE RECORDS? [J/N]\n");
if ($db =~ /^j/i) {
  if (!$ftp) { print " - NEP: geen records aangemaakt (geen verbinding met een server)\n"; }
  else {
    # Net als het echte programma: updates.txt van de server halen, de lege records toevoegen en terugzetten.
    my ($fh, $tmp) = tempfile(UNLINK => 1); close $fh;
    $ftp->get("cgi-bin/$app/updates.txt", $tmp) or die "updates.txt niet op te halen\n";
    open(my $in, '<', $tmp) or die; my $old = do { local $/; <$in> }; close $in;
    $old = '' unless defined $old; $old .= "\n" if $old ne '' && $old !~ /\n$/;
    my $oldn = () = $old =~ /\n/g;
    my $title = exists $cfg{record_title} ? $cfg{record_title} : sprintf('Een nog niet beschreven record (%s)', substr(date3(), 0, 6));
    my $new = '';
    for my $f (@files) {
      (my $nr = $f) =~ s/\.[^.]+$//;
      my @rec = ('') x 35;
      @rec[0, 1, 2, 5] = ($ID1, $ID2, $nr, $title);
      $rec[32] = $rec[34] = date3() . '|system';
      $new .= join("\t", @rec) . "\n";
    }
    open(my $out, '>', $tmp) or die; binmode $out; print $out $old, $new; close $out;
    $ftp->put($tmp, "cgi-bin/$app/updates.txt") or die "updates.txt niet te uploaden\n";
    my $newn = $oldn + scalar(@files);
    print "\n - Aangemaakt i.v.m. nieuwe images : ", scalar(@files), " records (met in titelveld: \"$title\")\n\n";
    print " - Oude   update database op webserver bevatte : $oldn records\n - Nieuwe update database op webserver bevat nu : $newn records\n\n";
    my @t = localtime; my $log = sprintf('log-%02d%02d%02d-%02d%02d.txt', $t[5] % 100, $t[4] + 1, $t[3], $t[2], $t[1]);
    if (open(my $lg, '>', $log)) { print $lg "Records aangemaakt: ", join(' ', map { (my $n = $_) =~ s/\.[^.]+$//; $n } @files), "\n"; close $lg; }
    print " ====> Upload informatie weggeschreven naar logfile: $log\n ====> Vergeet niet om de originele images te archiveren!!\n";
  }
}
if ($ftp) {
  # Het echte programma zet ook een regel in misc/images.log op de server.
  my ($fh2, $tmp2) = tempfile(UNLINK => 1); close $fh2;
  my $have = $ftp->get('cgi-bin/misc/images.log', $tmp2) ? 1 : 0;
  open(my $o, '>>', $tmp2) or die; my @t = localtime;
  printf $o "%04d%02d%02d-%02d:%02d|images.exe|MOCK|01-01-2026|01-01-2026|%s|%d|A|-init-iv1-iv2-iv3-B-ftp1-ftp2-C%s|%s\n", $t[5] + 1900, $t[4] + 1, $t[3], $t[2], $t[1], $app, scalar(@files), ($db =~ /^j/i ? '-D' : ''), ($cfg{pc_user} || 'mock');
  close $o; $ftp->mkdir('cgi-bin/misc') unless $have; $ftp->put($tmp2, 'cgi-bin/misc/images.log'); $ftp->quit;
}
# Opruimen gebeurt echt, net als bij images.exe (stap E).
my $rm1 = ask(" > VERWIJDEREN (tijdelijk) aangemaakte mappen [small], [large] en [100pct]? [J/N]\n");
if ($rm1 =~ /^j/i) { remove_tree($_) for qw(small large 100pct); print " - Mappen small/large/100pct verwijderd\n"; }
my $rm2 = ask(" > VERWIJDEREN inhoud van de map [images] met bron images? [J/N]\n");
if ($rm2 =~ /^j/i) { unlink("images/$_") for @files; print " - ", scalar(@files), " bestanden uit map images verwijderd\n"; }
ask(" > Geef een Return/Enter om dit DOS-window te verlaten\n");
exit 0;
