package misc::sendmail;

# Nep-versie voor de lokale ZCBS: verstuurt niets, schrijft de e-mail in mailbox.txt.
use strict;
use warnings;
use Exporter;
our @ISA = qw(Exporter);
our @EXPORT = qw(&sendmail);
our @EXPORT_OK = qw(&sendmail);
our $error = '';
our $log = '';

sub sendmail {
    my %mail = @_;
    my $file = ($ENV{DOCUMENT_ROOT} || '.') . '/mailbox.txt';
    if (open(my $out, '>>', $file)) {
        print $out "===== " . scalar(localtime) . " =====\n";
        print $out "To:      " . ($mail{To} // '') . "\n";
        print $out "Cc:      " . ($mail{Cc} // '') . "\n";
        print $out "From:    " . ($mail{From} // '') . "\n";
        print $out "Subject: " . ($mail{Subject} // '') . "\n\n";
        print $out ($mail{Message} // '') . "\n\n";
        close($out);
    }
    return 1;
}

1;
