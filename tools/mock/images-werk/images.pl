#!/usr/bin/perl
# Nep-images.exe om de workflow op Mac/Linux te testen. Volgt het echte images.exe 3.27 zoals dat is gemeten (zie docs/zcbs-achtergrond.md,
# "De vragen van images.exe 3.27 in volgorde", en docs/042.pdf): dezelfde teksten, dezelfde volgorde van de vragen en dezelfde handelingen,
# en net als het echte programma doet het ook echt wat je antwoordt: bij J op "VERWIJDEREN ... [small], [large] en [100pct]" zijn die mappen weg.
#
# Zonder images-mock.ini uploadt het niets. Met images-mock.ini (zie images-mock.ini.example) doet het net als het echte programma een
# FTP-upload naar de (lokale) server en zet het de lege database records in updates.txt op de server (RETR, aanvullen, STOR).
# Net als een gewoon Perl-programma zonder $| = 1 buffert het zijn uitvoer als het niet in een terminal draait. Zet MOCK_UNBUFFERED=1 om dat uit te zetten.
use strict; use warnings; use File::Copy; use File::Path qw(make_path remove_tree); use FindBin qw($Bin); use Net::FTP; use File::Temp qw(tempfile); use Cwd qw(getcwd);
use lib "$Bin/..";
use MockImage qw(shrink_jpg);
$| = 1 if $ENV{MOCK_UNBUFFERED};

my %cfg;
if (open(my $ini, '<', "$Bin/images-mock.ini")) {
  while (<$ini>) { s/[\r\n]+$//; next if /^\s*(#|$)/; my ($k, $v) = split /\s*=\s*/, $_, 2; $cfg{lc $k} = defined $v ? $v : ''; }
  close $ini;
}
my $ftp_host = $cfg{ftp_host} || '';
my $ftp_user = $cfg{ftp_user} || 'ZCBS@historischekringheemskerk.nl';
my $base     = $cfg{ftp_base} || '/public_html';
my ($ID1, $ID2) = ($cfg{id1} || 'HKH', $cfg{id2} || '997');
sub date3 { my @t = localtime; return sprintf('%02d%02d%02d-%02d:%02d', $t[5] % 100, $t[4] + 1, $t[3], $t[2], $t[1]); }
sub ask { my ($q) = @_; print $q; my $a = <STDIN>; $a = '' unless defined $a; $a =~ s/[\r\n]+$//; return $a; }
sub yes { return $_[0] =~ /^\s*j/i; }
sub count_jpg { my ($d) = @_; return 0 unless -d $d; opendir(my $h, $d) or return 0; my $n = grep { /\.jpe?g$/i } readdir($h); closedir($h); return $n; }
my $line = '-' x 79;

print "$line\n$line\nDit ZCBS-hulpprogramma zorgt voor het:\nA. Inlezen en bepalen van de type images.\nB. Automatisch aanmaken van afgeleiden afbeeldingen (small/large/100pct).\n";
print "C. Uploaden via ftp van deze images naar de webserver.\nD. Aanmaken van de bijbehorende database records.\n\nDocumentatie:\nZie: www.zcbs.nl/docs/manual-images.pdf\n\n";
print "==> Vergeet niet om na afloop de originele afbeeldingen op te slaan!\n\n$line\nVersie 3.27 (MOCK) -- hkh-workflow    Info: nep-versie van het echte programma\n$line\n\n";

opendir(my $dh, "images") or die "Map images ontbreekt\n";
my @files = sort grep { /\.(jpe?g|tif|gif|png|bmp)$/i } readdir($dh); closedir($dh);
my @nrs = map { (my $n = $_) =~ s/\.[^.]+$//; $n } @files;
my @jpg = map { "$_.jpg" } @nrs;
print "- Huidige map is: ", getcwd(), "\n\n  Gevonden ", scalar(@files), " bestanden in map (\\images) van type: **** A ****\n\n";
ask("> Type ENTER/RETURN indien OK -- of kies een van de volgende opties/letters -- of breek het programma af:\n"
  . "   A : Bestandstype A: Vast aantal cijfers (= recordnummer); geen letters en andere tekens\n"
  . "   B : Bestandstype B: Vast aantal letters + vast alleen cijfers (= recordnummer)\n"
  . "   C : Bestandstype C: Willekeurig aantal letters en/of cijfers (naast - en _ geen andere tekens)\n");

# Bestaan small, large of 100pct al met bestanden, dan vraagt het programma of ze opnieuw aangemaakt moeten worden.
my @existing = grep { count_jpg($_) > 0 } qw(small large 100pct);
my $again = 1;
if (@existing) {
  print "\n- ATTENTIE: de map ", join(', ', map { "\\$_ bevat reeds " . count_jpg($_) . " .jpg bestanden" } @existing), "\n";
  $again = yes(ask("> Toch de afgeleide images small/large/100pct opnieuw aanmaken? [J/N]\n"));
}
my $r = 6;
if ($again) {
  print "\n\n$line\n- Resoluties voor aan te maken afgeleide afbeeldingen van de type A images?\n  ----------------------------------------------------------------\n";
  print "  1 = small: 100x100  -  large: 700x500  -  100pct: ongewijzigd\n  2 = small: 100x100  -  large: 700x500  -  100pct: max. 3000-2000\n  3 = small: 100x100  -  large: 700x500  -  100pct: max. 4000-3000\n";
  print "  ----------------------------------------------------------------\n  6 = small: 250x250  -  large: 700x500  -  100pct: ongewijzigd\n  7 = small: 250x250  -  large: 700x500  -  100pct: max. 3000-2000\n  8 = small: 250x250  -  large: 700x500  -  100pct: max. 4000-3000\n";
  print "  ----------------------------------------------------------------\n";
  while (1) {
    $r = ask("  Kies  1,2,3 (objecten,boeken,etc),  of kies  6,7,8 (foto's,etc)\n");
    last if $r =~ /^[1-36-8]$/;
  }
  my %max = (small => ($r <= 3 ? 100 : 250), large => 700, '100pct' => ($r % 5 == 2 ? 3000 : ($r % 5 == 3 ? 4000 : 0)));
  print "\n";
  for my $d (qw(small large 100pct)) {
    make_path($d);
    my $done = 0;
    for my $i (0 .. $#files) { $done++ if shrink_jpg("images/$files[$i]", "$d/$jpg[$i]", $max{$d}); }
    print "- $done (van de ", scalar(@files), ") images via Irfanview omgezet en geplaatst in submap: \\$d\n";
    sleep 1;
  }
}

print "\n\n$line\n";
my $up = ask("> UPLOADEN aangemaakte images in small/large/100pct naar webserver? [J/N]\n");
my ($ftp, $app) = (undef, '');
if (yes($up)) {
  my $tries = 0;
  LOGIN: while (1) {
    my $inst;
    while (1) {
      $inst = ask("> Nummer, naam of plaats instelling?\n");
      last if $inst =~ /^0*4$|heemskerk|historische/i;
      print "- ZCBS-instelling niet herkend. Geef een nummer, naam of plaats.\n";
    }
    print "> Welkom: ==== 004: Kring Heemskerk ====\n";
    my $pw = ask("> FTP wachtwoord van uw webserver?\n");
    if ($ftp_host eq '') { print "- NEP: geen ftp-server ingesteld (images-mock.ini); er wordt niets geupload.\n"; $app = 'archief'; last LOGIN; }
    $ftp = Net::FTP->new($ftp_host, Port => ($cfg{ftp_port} || 21), Passive => 1, Timeout => 30);
    if ($ftp && $ftp->login($ftp_user, $pw)) { $ftp->binary; last LOGIN; }
    undef $ftp;
    print "==ERROR== Onjuist wachtwoord opgegeven!\n";
    die "Te vaak een fout wachtwoord\n" if ++$tries >= 5;
  }
  if ($ftp) {
    my @apps = sort grep { defined $ftp->size("$base/cgi-bin/$_/$_.txt") } map { (my $n = $_) =~ s{.*/}{}; $n } ($ftp->ls("$base/cgi-bin"));
    print "> Beschikbare ZCBS-toepassingen:\n", map { "   - $_\n" } @apps;
    if (@apps == 1) { $app = $apps[0]; print "- ZCBS-toepassing: $app\n"; }
    else {
      while (1) {
        $app = ask("> Kies de ZCBS-toepassing (= tevens naam van de uploadmap op de webserver):\n");
        last if grep { $_ eq $app } @apps;
        print "- Onbekende toepassing: $app\n";
      }
    }
    # De submappen van de toepassing: alleen als er in /small al mappen staan.
    my @dirs = grep { /^d/ } $ftp->dir("$base/$app/small");
    my @names = map { my ($n) = $_ =~ /\s(\S+)\s*$/; $n } @dirs;
    print "- De webserver map ==/public_html/$app/== bevat: ", join('-', map { scalar(grep { /\.jpe?g$/i } map { s{.*/}{}r } $ftp->ls("$base/$app/$_")) } qw(small large)), " images\n";
    my $sub = '';
    if (@names) {
      print "- De webserver map ==/public_html/$app/== bevat de submappen:\n   0. : de root\n";
      my $k = 1; print "   ", $k++, ". : $_\n" for @names;
      my $c = ask("> Waar wilt u de images geuploaded hebben (kies 0-" . scalar(@names) . ")?\n   ==of==\n   Images naar een nieuwe submap? ==Geef dan alleen een ENTER/RETURN==\n");
      if ($c =~ /^\d+$/ && $c > 0 && $c <= @names) { $sub = "$names[$c-1]/"; }
      elsif ($c eq '') { my $n = ask("> Geef de naam van de nieuwe submap\n"); $ftp->mkdir("$base/$app/$_/$n") for qw(small large); $ftp->mkdir("$base/cgi-bin/$app/100pct/$n"); $sub = "$n/"; }
    } else {
      print "- Er zijn geen submappen.\n";
      my $n = ask("> Geef een ENTER/RETURN of ==de naam van deze submap== (de images komen dan in de root van $app)\n");
      if ($n ne '') { $ftp->mkdir("$base/$app/$_/$n") for qw(small large); $ftp->mkdir("$base/cgi-bin/$app/100pct/$n"); $sub = "$n/"; }
    }
    my %target = (small => "$base/$app/small/$sub", large => "$base/$app/large/$sub", '100pct' => "$base/cgi-bin/$app/100pct/$sub");
    my $exist = 0;
    for my $d (qw(small large 100pct)) { for my $j (@jpg) { $exist++ if defined $ftp->size("$target{$d}$j"); } }
    my $over = 'j';
    $over = ask("> Oude images (met dezelfde naam) in small/large/100pct op webserver OVERSCHRIJVEN? [J/N]\n") if $exist;
    for my $d (qw(small large 100pct)) {
      my $n = 0;
      for my $j (@jpg) {
        next if defined $ftp->size("$target{$d}$j") && !yes($over);
        $n++ if $ftp->put("$d/$j", "$target{$d}$j");
      }
      print "- $n (van de ", scalar(@jpg), ") images via FTP geplaatst op webserver in de map /$d", ($sub ne '' ? "/$sub" : ''), "\n";
    }
  }
}

# Ook na een nee op uploaden wordt naar de records gevraagd (zo staat het in het log van het echte programma).
print "\n\n$line\n";
my $db = ask("> AANMAKEN en UPLOADEN van de bijbehorende " . scalar(@files) . " nieuwe lege DATABASE RECORDS? [J/N]\n");
my $made_add = 0;
if (yes($db)) {
  if (!$ftp) { print "- NEP: geen records aangemaakt (geen verbinding met een server)\n"; }
  else {
    # Net als het echte programma: updates.txt van de server halen, de lege records toevoegen en terugzetten; de nieuwe regels staan ook in updates-add.txt.
    my ($fh, $tmp) = tempfile(UNLINK => 1); close $fh;
    $ftp->get("$base/cgi-bin/$app/updates.txt", $tmp) or die "updates.txt niet op te halen\n";
    open(my $in, '<', $tmp) or die; my $old = do { local $/; <$in> }; close $in;
    $old = '' unless defined $old; $old .= "\n" if $old ne '' && $old !~ /\n$/;
    my $oldn = () = $old =~ /\n/g;
    my $title = exists $cfg{record_title} ? $cfg{record_title} : sprintf('Een nog niet beschreven record (%s)', substr(date3(), 0, 6));
    my $new = '';
    for my $nr (@nrs) {
      my @rec = ('') x 35;
      @rec[0, 1, 2, 5] = ($ID1, $ID2, $nr, $title);
      $rec[32] = $rec[34] = date3() . '|system';
      $new .= join("\t", @rec) . "\n";
    }
    open(my $add, '>', 'updates-add.txt') or die; binmode $add; print $add $new; close $add; $made_add = 1;
    open(my $out, '>', $tmp) or die; binmode $out; print $out $old, $new; close $out;
    $ftp->put($tmp, "$base/cgi-bin/$app/updates.txt") or die "updates.txt niet te uploaden\n";
    my $newn = $oldn + scalar(@files);
    print "\n- Aangemaakt i.v.m. nieuwe images : ", scalar(@files), " records (met in titelveld: \"$title\")\n\n";
    print "- Oude   update database op webserver bevatte : $oldn records\n- Nieuwe update database op webserver bevat nu : $newn records\n\n";
    my @t = localtime; my $log = sprintf('log-%02d%02d%02d-%02d%02d.txt', $t[5] % 100, $t[4] + 1, $t[3], $t[2], $t[1]);
    if (open(my $lg, '>', $log)) { print $lg "Records aangemaakt: ", join(' ', @nrs), "\n"; close $lg; }
    print "====> Upload informatie weggeschreven naar logfile: $log\n====> Vergeet niet om de originele images te archiveren!!\n";
  }
}
if ($ftp) {
  # Het echte programma zet ook een regel in misc/images.log op de server.
  my ($fh2, $tmp2) = tempfile(UNLINK => 1); close $fh2;
  my $have = $ftp->get("$base/cgi-bin/misc/images.log", $tmp2) ? 1 : 0;
  open(my $o, '>>', $tmp2) or die; my @t = localtime;
  printf $o "%04d%02d%02d-%02d:%02d|images.exe|3.27|13-07-2021|21-01-2022|%s|%d|A|-init-iv1-iv2-iv3-B-ftp1-ftp2-C%s|%s\n", $t[5] + 1900, $t[4] + 1, $t[3], $t[2], $t[1], $app, scalar(@files), (yes($db) ? '-up1-up2' : ''), ($cfg{pc_user} || 'mock');
  close $o; $ftp->mkdir("$base/cgi-bin/misc") unless $have; $ftp->put($tmp2, "$base/cgi-bin/misc/images.log"); $ftp->quit;
}

# Opruimen gebeurt echt, net als bij images.exe: wat je met J beantwoordt, is daarna weg.
print "\n\n";
if (yes(ask("> VERWIJDEREN (tijdelijk) aangemaakte mappen [small], [large] en [100pct]? [J/N]\n"))) { remove_tree($_) for qw(small large 100pct); print "- Mappen small/large/100pct verwijderd\n"; }
print "\n\n";
if ($made_add || -f 'updates-add.txt') {
  if (yes(ask("> VERWIJDEREN (tijdelijk) PC-bestand met nieuwe database records? [J/N]\n"))) { unlink('updates-add.txt'); print "- Bestand updates-add.txt verwijderd\n"; }
  print "\n\n";
}
if (yes(ask("> VERWIJDEREN inhoud van de map [images] met bron images? [J/N]\n"))) { unlink("images/$_") for @files; print "- ", scalar(@files), " bestanden uit map images verwijderd\n"; }
print "\n";
ask("> Geef een Return/Enter om dit DOS-window te verlaten\n");
exit 0;
