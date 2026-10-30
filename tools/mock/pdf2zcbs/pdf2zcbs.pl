#!/usr/bin/perl
# Nep-pdf2zcbs.exe om de workflow op Mac/Linux te testen. Haalt tekst uit de pdf met pdftotext als dat geïnstalleerd is.
#
# Net als de echte pdf2zcbs.exe bij HKH: staat er een pdf2zcbs.ini naast het programma (dat is hier zo), dan zijn de
# vragen al beantwoord en toont het programma **niets** en wacht het nergens op. Zonder dat bestand stelt het dezelfde
# vragen als pdf2zcbs.exe (zie docs/050.pdf, hoofdstuk 3) en toont het zijn voortgang.
# Buffert zijn uitvoer zoals een gewoon Perl-programma; MOCK_UNBUFFERED=1 zet dat uit.
use strict; use warnings; use File::Copy; use File::Path qw(make_path remove_tree);
$| = 1 if $ENV{MOCK_UNBUFFERED};
use FindBin qw($RealBin);
use lib "$RealBin/..";
use MockImage qw(pdf_to_jpg find_tool);
# Met pdf2zcbs.ini naast het programma: geen vragen en geen uitvoer (zoals de echte exe bij HKH).
my $silent = -f "$RealBin/pdf2zcbs.ini";
sub say_ { print @_ unless $silent; }
sub ask { my ($q) = @_; return '' if $silent; print $q; my $a = <STDIN>; $a = '' unless defined $a; $a =~ s/[\r\n]+$//; return $a; }

say_("-" x 76, "\n pdf2zcbs: versie MOCK  Info: hkh-workflow\n", "-" x 76, "\n");
# Net als de echte pdf2zcbs.exe (handleiding 050, hfdst 11.14): bij elke run wordt de map output geleegd.
if (-d "output") { remove_tree("output"); say_(" Map output geleegd (vorige resultaten zijn weg).\n"); }
opendir(my $dh, ".") or die; my @pdfs = sort grep { /\.pdf$/i && -f $_ } readdir($dh); closedir($dh);
if (!@pdfs) { say_(" Geen PDF gevonden in de huidige map.\n"); ask(" Type return to exit\n"); exit 1; }
make_path("output/pdf", "output/ocr", "output/small", "output/large", "output/log");
# Eerste pagina als echte jpg met de gegeven hoogte (zie ../MockImage.pm: pdftoppm, anders sips, anders een voorbeeldafbeelding).
sub jpg { my ($pdf, $out, $height) = @_; pdf_to_jpg($pdf, $out, $height); }
if (@pdfs == 1) {
  say_(" Aanwezige PDF in de huidige map:\n - $pdfs[0]\n");
  my $s = ask(" > Deze PDF splitsen in (groepen van) losse pagina's (default = N)?\n");
}
move($_, "output/pdf/$_") for @pdfs;
ask(" > PDF's in map output/pdf verwerken naar txt + jpg's? (default = J)\n");
ask(" > Betreft het (presentatie) slides? (default= N)\n");
for my $p (@pdfs) {
  (my $base = $p) =~ s/\.pdf$//i;
  my $pdftotext = find_tool('pdftotext');
  my $text = $pdftotext ? (`"$pdftotext" output/pdf/$p - 2>/dev/null` // '') : '';
  my @zinnen = grep { /\S/ } split(/(?<=[.!?])\s+/, $text);
  # Een scan heeft geen tekstlaag, dus pdftotext vindt niets; de echte pdf2zcbs.exe doet OCR en levert wel tekst. Zet dan een vaste tekst neer.
  push @zinnen, "[mock] Geen tekst in $p gevonden; de echte pdf2zcbs.exe zet hier de herkende tekst (OCR)." unless @zinnen;
  open(my $fh, '>', "output/ocr/$base.txt") or die; print $fh "$_\n" for @zinnen; close $fh;
  say_(" Verwerking: $p : bevat ", scalar(@zinnen), " zinnen --> output/ocr/$base.txt)\n");
  jpg("output/pdf/$p", "output/small/$base.jpg", 193);
  jpg("output/pdf/$p", "output/large/$base.jpg", 500);
  sleep 1;
}
say_(" Tevens de small- en large-images van de 1e pagina van de PDF's aangemaakt --> output/small/*.jpg en output/large/*.jpg\n");
say_("-" x 76, "\n That's all folks\n");
ask(" Type return to exit\n");
exit 0;
