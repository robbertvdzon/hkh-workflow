#!/usr/bin/perl
# Nep-images.exe om de workflow op Mac/Linux te testen. Stelt dezelfde vragen als images.exe
# (zie docs/042.pdf, hoofdstuk 5) maar uploadt niets. Net als een gewoon Perl-programma zonder
# $| = 1 buffert het zijn uitvoer als het niet in een terminal draait. Zet MOCK_UNBUFFERED=1 om
# dat uit te zetten.
use strict; use warnings; use File::Copy; use File::Path qw(make_path remove_tree);
$| = 1 if $ENV{MOCK_UNBUFFERED};
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
for my $d (qw(small large 100pct)) {
  make_path($d);
  for my $f (@files) { (my $j = $f) =~ s/\.[^.]+$/.jpg/; copy("images/$f", "$d/$j"); }
  print " - ", scalar(@files), " (van de ", scalar(@files), ") images via Irfanview omgezet en geplaatst in submap: \\$d\n";
  sleep 1;
}
my $up = ask(" > UPLOADEN aangemaakte images in small/large/100pct naar webserver? [J/N]\n");
if ($up =~ /^j/i) {
  ask(" > Nummer, naam of plaats instelling?\n");
  print " > Welkom: ==== 004: Historische Kring Heemskerk (NEP) ====\n";
  ask(" > FTP wachtwoord van uw webserver?\n");
  print " > Beschikbare ZCBS-toepassingen:\n   - archief\n   - beeldbank\n   - test\n";
  my $app = ask(" > Kies de ZCBS-toepassing (= tevens naam van de uploadmap op de webserver):\n");
  print " - NEP: niets geupload naar $app\n";
  my $db = ask(" > AANMAKEN en UPLOADEN van de bijbehorende ", scalar(@files), " nieuwe lege DATABASE RECORDS? [J/N]\n");
  print " - NEP: geen records aangemaakt\n" if $db =~ /^j/i;
}
# Opruimen gebeurt echt, net als bij images.exe (stap E).
my $rm1 = ask(" > VERWIJDEREN (tijdelijk) aangemaakte mappen [small], [large] en [100pct]? [J/N]\n");
if ($rm1 =~ /^j/i) { remove_tree($_) for qw(small large 100pct); print " - Mappen small/large/100pct verwijderd\n"; }
my $rm2 = ask(" > VERWIJDEREN inhoud van de map [images] met bron images? [J/N]\n");
if ($rm2 =~ /^j/i) { unlink("images/$_") for @files; print " - ", scalar(@files), " bestanden uit map images verwijderd\n"; }
ask(" > Geef een Return/Enter om dit DOS-window te verlaten\n");
exit 0;
