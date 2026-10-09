#!/usr/bin/perl
# Nep-pdf2zcbs.exe om de workflow op Mac/Linux te testen. Stelt dezelfde vragen als pdf2zcbs.exe
# (zie docs/050.pdf, hoofdstuk 3). Haalt tekst uit de pdf met pdftotext als dat geïnstalleerd is.
# Buffert zijn uitvoer zoals een gewoon Perl-programma; MOCK_UNBUFFERED=1 zet dat uit.
use strict; use warnings; use File::Copy; use File::Path qw(make_path remove_tree);
$| = 1 if $ENV{MOCK_UNBUFFERED};
sub ask { my ($q) = @_; print $q; my $a = <STDIN>; $a = '' unless defined $a; $a =~ s/[\r\n]+$//; return $a; }

print "-" x 76, "\n pdf2zcbs: versie MOCK  Info: hkh-workflow\n", "-" x 76, "\n";
# Net als de echte pdf2zcbs.exe (handleiding 050, hfdst 11.14): bij elke run wordt de map output geleegd.
if (-d "output") { remove_tree("output"); print " Map output geleegd (vorige resultaten zijn weg).\n"; }
opendir(my $dh, ".") or die; my @pdfs = sort grep { /\.pdf$/i && -f $_ } readdir($dh); closedir($dh);
if (!@pdfs) { print " Geen PDF gevonden in de huidige map.\n"; ask(" Type return to exit\n"); exit 1; }
make_path("output/pdf", "output/ocr", "output/small");
if (@pdfs == 1) {
  print " Aanwezige PDF in de huidige map:\n - $pdfs[0]\n";
  my $s = ask(" > Deze PDF splitsen in (groepen van) losse pagina's (default = N)?\n");
}
move($_, "output/pdf/$_") for @pdfs;
ask(" > PDF's in map output/pdf verwerken naar txt + jpg's? (default = J)\n");
ask(" > Betreft het (presentatie) slides? (default= N)\n");
for my $p (@pdfs) {
  (my $base = $p) =~ s/\.pdf$//i;
  my $text = `pdftotext output/pdf/$p - 2>/dev/null` // '';
  my @zinnen = grep { /\S/ } split(/(?<=[.!?])\s+/, $text);
  open(my $fh, '>', "output/ocr/$base.txt") or die; print $fh "$_\n" for @zinnen; close $fh;
  print " Verwerking: $p : bevat ", scalar(@zinnen), " zinnen --> output/ocr/$base.txt)\n";
  sleep 1;
}
print " Tevens de thumbnails van de 1e pagina van de PDF's aangemaakt --> output/small/*.jpg (NEP: niet echt)\n";
print "-" x 76, "\n That's all folks\n";
ask(" Type return to exit\n");
exit 0;
