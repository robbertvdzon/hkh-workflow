#!/usr/bin/perl
#
# Nagebouwde ZCBS voor de integratietests van hkh-workflow. Dit is eigen code en bevat niets van de echte ZCBS: het doet alleen na wat de
# app van buitenaf ziet (zie docs/integratietests.md). De gegevens staan zoals bij de echte ZCBS in tekstbestanden, zodat dezelfde
# beginstand ook met de echte ZCBS werkt:
#
#   cgi-bin/archief/archief.txt          de database (tab-gescheiden, 35 kolommen, eerste regel is de kop)
#   cgi-bin/archief/updates.txt          wijzigingen: de laatste regel per recordnummer wint
#   cgi-bin/misc/permit.lst              gebruikers: collectie <tab> rechten <tab> ip <tab> naam - wachtwoord
#   archief/logging/lock_object.txt      wie welk record voor het laatst opende (de vergrendeling)
#   archief/logging/sessions.txt         wie is ingelogd; leegmaken laat alle sessies verlopen (alleen hier, voor de tests)
#
# Het bestand is bewust latin-1-neutraal: alles gaat als bytes door het script, net als bij ZCBS.
use strict;
use warnings;
use Fcntl qw(:flock);

binmode(STDIN);
binmode(STDOUT);

my $LOCK_MINUTES = 10;
my $WIDTH        = 5;
my $COLLECTION   = 'archief';
my $UPDATES      = "$COLLECTION/updates.txt";
my $MASTER       = "$COLLECTION/$COLLECTION.txt";
my $PERMIT       = 'misc/permit.lst';
my $LOGDIR       = "../$COLLECTION/logging";
my $LOCKS        = "$LOGDIR/lock_object.txt";
my $SESSIONS     = "$LOGDIR/sessions.txt";

# De velden van het formulier, in de volgorde van het echte formulier.
my $MAKE_CHOICE = '==Maak uw keuze==';
my $OTHER       = '== Anders ==';
my %OPTIONS     = (
    a22 => [$MAKE_CHOICE, 'HKH Jan de Ruijter', 'Pierre Janssens', 'van Wijk', 'Jaap Schoen', 'Onbekend', $OTHER],
    a24 => [
        $MAKE_CHOICE, 'Bedrijven/Winkels/Detailhandel/Ondernemingen', 'Begraafplaatsen', 'Bestemmingsplan', 'Bloemen- en groenteteelt, Aardbeienteelt',
        'Boerderijen en Tuingebied', 'Brandweer, Politie en Veiligheid', 'Festiviteiten en Volksfeesten', 'Financiele zaken (Banken)',
        'Gebouwen en Terreinen', 'Gereedschappen', 'Gemeente, bestuur, vergunningen en politiek', 'Geschiedenis', 'Kastelen en Monumenten',
        'Kerken en Religie', 'Kunst en Cultuur', 'Landbouw en Veeteelt', 'Locale geschiedenis en bewoners', 'Markt', 'Milieuzaken en Afvalverwerking',
        'Onderwijs, Scholen en Kinderopvang', 'Openbare leven (bioscoop, café, horeca)', 'Recreatie', 'Sport en Sportverenigingen', 'Scheepvaart',
        'Verenigingsleven en Gezelschappen', 'Vergunningen', 'Verkeer en Wegen', 'Vluchtelingen, Asielzoekers', $OTHER,
    ],
    a3 => [
        $MAKE_CHOICE, 'Advertentie', 'Diploma', 'Heemskring', 'Krantenartikel', 'Kroniek', 'Notariële akte', 'Officiële bekendmaking', 'Overeenkomst',
        'Tekening', 'Kaart/Plattegrond', 'Uitnodiging', 'Verzameling gegevens', $OTHER,
    ],
    a9 => [
        $MAKE_CHOICE, 'Noord Hollands Dagblad', 'Heemskerkse Courant', 'Nieuwsblad de Kennemer', 'Kennemerland op Zondag', 'Dagblad Kennemerland',
        'Museum Kennemerland', 'IJmuider Courant', 'Gemeente Heemskerk', 'Historische Kring Heemskerk', 'Typhoon', 'Onbekend', $OTHER,
    ],
    a25 => [$MAKE_CHOICE, 'Heemskerk', 'Beverwijk', 'Castricum', 'Wijk aan Zee', 'Velsen', 'Uitgeest', $OTHER],
);
my @FIELDS = (
    ['text',     5,  'Titel',                               100],
    ['text',     7,  'Auteur(s)',                           40],
    ['text',     8,  'Datum',                               20],
    ['textarea', 6,  'Omschrijving',                        3],
    ['select',   22, 'Oorspronkelijk archief'],
    ['multi',    24, 'Thema'],
    ['select',   3,  'Type publicatie'],
    ['text',     4,  'Trefwoord',                           20],
    ['select',   9,  'Uitgever'],
    ['text',     11, "Totaal aantal pagina's dit document", 6],
    ['select',   25, 'Plaats'],
    ['text',     26, 'Straatnaam?',                         40],
    ['text',     18, 'Aanvullend beschikbaar materiaal',    40],
    ['extra',    10, 'Conditie',                            20],
    ['text',     12, 'Verwerving',                          20],
    ['text',     13, 'Eigenaar',                            30],
    ['text',     14, 'ZCBS inv.nr.',                        30],
    ['radio',    21, 'Plaatsing op internet'],
    ['text',     16, 'Doos nummer',                         10],
    ['extra',    15, 'Map nummer',                          10],
    ['text',     27, 'Locatie op PC',                       120],
    ['textarea', 19, 'Interne opm.',                        2],
);
# Velden waar ZCBS een opmerking achter een | aan vastmaakt (zie RecordRebuilder in de app).
my %SUBFIELD = map { $_ => 1 } (7, 8, 9, 10, 12, 13, 14, 15, 16, 17, 18, 20, 22, 24, 26, 30);

sub main {
    my %query = parse_urlencoded($ENV{QUERY_STRING} // '');
    if (($ENV{HTTP_USER_AGENT} // '') =~ /\bcurl\b/i) { page_raw('<html><head></head><body>==Sorry:3==</body></html>'); return; }

    my $method = $ENV{REQUEST_METHOD} // 'GET';
    if ($method eq 'POST') {
        my $body = '';
        read(STDIN, $body, $ENV{CONTENT_LENGTH} // 0);
        if (($ENV{CONTENT_TYPE} // '') =~ m{^multipart/form-data;\s*boundary="?([^";]+)}i) {
            save_record(parse_multipart($body, $1));
            return;
        }
        my %form = parse_urlencoded($body);
        if (exists $form{user}) { login(\%form); return; }
    }
    if (($query{user} // '') eq 'reset') { logout(); return; }

    my ($user, $level, $state) = current_user();
    my $ident = $query{ident};
    my $upd   = $query{upd_form} // '';
    if (($query{search} // '') eq 'LAST')                  { last_page($user); }
    elsif (defined $ident && $upd eq 'new')                { new_page($user, $level, $state); }
    elsif (defined $ident && $upd eq 'edt')                { edit_page($ident, $user, $level, $state); }
    elsif (defined $ident)                                 { view_page($ident); }
    else                                                   { home_page($user); }
}

# ---- inloggen ----

sub users {
    my %users;
    open(my $in, '<', $PERMIT) or return %users;
    while (my $line = <$in>) {
        chomp $line;
        $line =~ s/\r$//;
        next if $line =~ /^\s*#/;
        my ($collection, $level, undef, $rest) = split(/\s+/, $line, 4);
        next unless defined $rest && $collection eq $COLLECTION;
        my ($name, $password) = split(/\s+-\s+/, $rest, 2);
        $users{$name} = { level => $level, password => $password } if defined $password;
    }
    close($in);
    return %users;
}

sub password_hash { return substr(crypt($_[0], '99'), 2); }

sub login {
    my ($form) = @_;
    my $name     = $form->{user} // '';
    my $password = $form->{pswd} // '';
    my %users    = users();
    if ($users{$name} && $users{$name}{password} eq $password) {
        add_session($name);
        my $hash = password_hash($password);
        print "Set-Cookie: zcbs-user=$name|$hash; SameSite=lax\n";
        my $ident = $form->{ident};
        if (defined $ident && $ident ne '') { view_page($ident); return; }
        home_page($name);
        return;
    }
    print "Set-Cookie: zcbs-user=$name|fout; SameSite=lax\n";
    page('Autorisatie interne gebruiker', login_form('<p><strong>Gebruikersnaam/wachtwoord combinatie niet herkend !</strong></p>'));
}

sub logout {
    print "Set-Cookie: zcbs-user=; SameSite=lax\n";
    page('Autorisatie interne gebruiker', login_form(''));
}

sub add_session {
    my ($name) = @_;
    open(my $out, '>>', $SESSIONS) or return;
    flock($out, LOCK_EX);
    print $out "$name\n";
    close($out);
}

# @return (naam, rechten, stand): stand is 'ok', 'verlopen' (wel een cookie, maar geen sessie meer) of 'geen'.
sub current_user {
    my $cookies = $ENV{HTTP_COOKIE} // '';
    return (undef, undef, 'geen') unless $cookies =~ /(?:^|;\s*)zcbs-user=([^;]*)/;
    my ($name, $hash) = split(/\|/, $1, 2);
    return (undef, undef, 'geen') unless defined $name && $name ne '';
    my %users = users();
    return (undef, undef, 'verlopen') unless $users{$name} && password_hash($users{$name}{password}) eq ($hash // '');
    open(my $in, '<', $SESSIONS) or return (undef, undef, 'verlopen');
    my @active = <$in>;
    close($in);
    chomp @active;
    return (undef, undef, 'verlopen') unless grep { $_ eq $name } @active;
    return ($name, $users{$name}{level}, 'ok');
}

sub may_edit { my ($level) = @_; return defined $level && $level =~ /^1/; }

# ---- de database ----

sub records {
    my %records;
    for my $file ($MASTER, $UPDATES) {
        open(my $in, '<', $file) or next;
        while (my $line = <$in>) {
            $line =~ s/\r?\n$//;
            my @fields = split(/\t/, $line, -1);
            next unless @fields > 2 && $fields[2] =~ /^\d+$/;
            push(@fields, '') while @fields < 35;
            $records{$fields[2]} = \@fields;
        }
        close($in);
    }
    return %records;
}

sub append_record {
    my ($fields) = @_;
    open(my $out, '>>', $UPDATES) or die "Kan $UPDATES niet schrijven: $!";
    flock($out, LOCK_EX);
    print $out join("\t", @$fields) . "\n";
    close($out);
}

sub stamp {
    my ($user) = @_;
    my @t = localtime();
    return sprintf('%02d%02d%02d-%02d:%02d|%s', ($t[5] + 1900) % 100, $t[4] + 1, $t[3], $t[2], $t[1], $user);
}

# ---- de vergrendeling ----

sub minutes_now { return int(time() / 60); }

sub latest_lock {
    my ($number) = @_;
    open(my $in, '<', $LOCKS) or return;
    my @found;
    while (my $line = <$in>) {
        chomp $line;
        my @item = split(/\|/, $line, -1);
        @found = @item if @item > 5 && $item[2] eq $number;
    }
    close($in);
    return @found;
}

# @return (minuten, door wie) als een ander het record vergrendeld heeft, anders niets.
sub locked_by_other {
    my ($number, $user) = @_;
    my @lock = latest_lock($number);
    return () unless @lock;
    my $left = $LOCK_MINUTES - (minutes_now() - $lock[0]);
    return () if $left <= 0 || $lock[3] ne 'edt' || $lock[5] eq $user;
    return ($left, $lock[5]);
}

sub write_lock {
    my ($number, $user, $action) = @_;
    open(my $out, '>>', $LOCKS) or return;
    flock($out, LOCK_EX);
    my $ip = $ENV{REMOTE_ADDR} // '';
    print $out minutes_now() . "|$ip|$number|$action|0|$user|1-99999\n";
    close($out);
}

# ---- pagina's ----

sub page {
    my ($title, $body) = @_;
    page_raw(<<"HTML");
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 3.2//EN">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>$title</title>
</head>
<body bgcolor="#66CCFF">
<h1>Archief (nagebouwde ZCBS voor de tests)</h1>
$body
</body>
</html>
HTML
}

sub page_raw {
    my ($html) = @_;
    print "Content-type: text/html; charset=iso-8859-1\n\n";
    print $html;
}

sub login_form {
    my ($message) = @_;
    return <<"HTML";
<h2>Autorisatie interne gebruiker</h2>
$message
<form method="POST" action="/cgi-bin/$COLLECTION.pl">
Uw naam: <input type="text" name="user" value="">
<input type="password" name="pswd" value="">
<input type="submit" value="OK">
</form>
HTML
}

sub home_page {
    my ($user) = @_;
    my $message = defined $user ? '<h3 align="center"><font color="green" size="+2"><strong>Gebruikersnaam/wachtwoord combinatie herkend!</strong></font></h3>' : '';
    page('Archief Historische Kring Heemskerk', $message . "\n<a href=\"/cgi-bin/$COLLECTION.pl?user=reset\">Uitloggen</a>");
}

sub session_expired {
    page('Autorisatie interne gebruiker', login_form('<h3 align="center">Uw sessie is verlopen.<br>Log opnieuw in </h3><p>'));
}

sub not_available {
    my ($number) = @_;
    page('(publicatie uit de bibliotheek Historische Kring Heemskerk)', qq{<center><strong><font color="red" size="+2">Database record nr. $number is niet beschikbaar</font></strong></center>});
}

sub locked_page {
    my ($number, $minutes, $by) = @_;
    page('Tijdelijk geblokkeerd object', <<"HTML");
<h2>Tijdelijk geblokkeerd object</h2>
    Het object <strong>$number</strong> is nog voor <strong>$minutes minuten</strong> door een andere
    gegevensbeheerder (<em>$by</em>) voor bewerking in gebruik.
HTML
}

sub last_page {
    my ($user) = @_;
    my %records = records();
    my @numbers = sort { $a <=> $b } grep { defined $user || $records{$_}[5] ne '' } keys %records;
    my $last = @numbers ? $numbers[-1] : '0000';
    page("Zoekresultaat Archief Heemskerk : LAST", <<"HTML");
<script type="text/javascript">
 function WebStorage(){ localStorage.zcbs_archief = "0000 $last 0000"; }
</script>
<a href="/cgi-bin/$COLLECTION.pl?ident=$last">$last</a>
HTML
}

sub new_page {
    my ($user, $level, $state) = @_;
    return session_expired() if $state eq 'verlopen';
    return not_available('0') if !may_edit($level);
    my %records = records();
    my $format = sub { return sprintf("%0${WIDTH}d", $_[0]); };
    my @cells;
    my $next = 1;
    for my $n (sort { $a <=> $b } keys %records) {
        if ($n > $next) {
            if ($n - $next >= 3) { push(@cells, $format->($next) . '-' . $format->($n - 1)); }
            else                 { push(@cells, map { $format->($_) } ($next .. $n - 1)); }
        }
        $next = $n + 1;
    }
    push(@cells, $format->($next) . '-');
    my $row = join('', map { "<td>&nbsp;$_&nbsp;</td>" } @cells);
    page('Archief Historische Kring Heemskerk', <<"HTML");
<strong>Niet gebruikte (dus vrije) record/databasenummers:</strong>
<br>
<table border="0" cellspacing="0" cellpadding="2">
<tr>
$row</tr>
</table>
HTML
}

sub view_page {
    my ($number) = @_;
    $number = pad($number);
    my %records = records();
    my $record = $records{$number};
    return not_available($number) unless $record;
    my $title    = $record->[5];
    my $internet = ($record->[21] eq '' || $record->[21] eq '0') ? 'nee' : 'ja';
    page("$title (publicatie $number uit de bibliotheek Historische Kring Heemskerk)", <<"HTML");
<table border="0">
<tr><th align="left" valign="top" nowrap>Documentnummer</th>
    <th align="center" valign="top"> : </th>
    <td>$number</td>
</tr>
<tr><th align="left" valign="top" nowrap>Titel</th>
    <th align="center" valign="top"> : </th>
    <td align="left" valign="top">$title</td>
</tr>
<tr><th align="left" valign="top" nowrap>Plaatsing op internet</th>
    <th align="center" valign="top"> : </th>
    <td align="left" valign="top">$internet</td>
</tr>
</table>
HTML
}

sub edit_page {
    my ($number, $user, $level, $state) = @_;
    $number = pad($number);
    return session_expired() if $state eq 'verlopen';
    my %records = records();
    my $record = $records{$number};
    return not_available($number) if $state eq 'geen' || !$record;
    if (!may_edit($level)) { view_page($number); return; }
    my ($minutes, $by) = locked_by_other($number, $user);
    if (defined $minutes) { locked_page($number, $minutes, $by); return; }
    write_lock($number, $user, 'edt');
    page($record->[5], form_html($number, $record, $user));
}

sub attr {
    my ($value) = @_;
    $value //= '';
    $value =~ s/"/&quot;/g;
    return $value;
}

sub form_html {
    my ($number, $record, $user) = @_;
    my $rows = '';
    for my $field (@FIELDS) {
        my ($kind, $i, $label, $size) = @$field;
        $rows .= qq{<tr><th align="left" valign="top" nowrap>$label</th>\n    <th align="center" valign="top" nowrap> &nbsp; : </th>\n    <td>}
            . control_html($kind, $i, $record->[$i], $size) . "</td>\n</tr>\n";
    }
    my $a0    = attr(join("\t", @$record));
    my $a32   = attr($record->[32]);
    my $a33   = attr($record->[33]);
    my $a34   = attr(stamp($user));
    return <<"HTML";
<form name=zcbsform method="POST" enctype="multipart/form-data" accept-charset="ISO-8859-1" action="/cgi-bin/$COLLECTION.pl">
<table border="0" cellspacing="0" cellpadding="1" id="table3b">
<tr><th align="left" valign="top" nowrap>Documentnummer</th>
    <th width="40" align="center" valign="top" nowrap> &nbsp; : </th>
    <td>$number</td>
</tr>
$rows</table>
<input type="hidden" name="ident"      value="$number">
<input type="hidden" name="upd_form"   value="edt">
<input type="hidden" name="display"    value="list">
<input type="hidden" name="write"      value="1">
<input type="hidden" name="a32"        value="$a32">
<input type="hidden" name="a33"        value="$a33">
<input type="hidden" name="a34"        value="$a34">
<input type="hidden" name="onload"     value="$number">
<input type="hidden" name="ident_next" value="$number">
<input type="hidden" name="a0"         value="$a0">
<input type="submit" class="button" value="VERWERK">
</form>
HTML
}

sub control_html {
    my ($kind, $i, $value, $size) = @_;
    my $name = "a$i";
    if ($kind eq 'text') { return qq{<input type="text" name="$name" id="id_form_$name" size="$size" value="} . attr($value) . qq{">\n}; }
    if ($kind eq 'extra') {
        my ($main, $extra) = split(/\|/, $value, 2);
        return qq{<input type="text" name="$name" id="id_form_$name" size="$size" value="} . attr($main) . qq{">\n}
            . qq{<br><span><strong>opmerking:</strong> <input type="text" name="${name}x" size="50" value="} . attr($extra) . qq{"></span>};
    }
    if ($kind eq 'textarea') {
        (my $text = $value) =~ s/<br>/\n/g;
        return qq{<textarea name="$name" id="id_form_$name" rows="$size" cols="90">$text</textarea>};
    }
    if ($kind eq 'radio') {
        my $on = ($value ne '' && $value ne '0');
        my $yes = $on ? $value : '9988';
        return qq{<input type="radio" name="$name" } . ($on ? '' : 'CHECKED') . qq{ value="0"> neen &nbsp;\n}
            . qq{<input type="radio" name="$name" } . ($on ? 'CHECKED' : '') . qq{ value="$yes"> ja\n};
    }
    return select_html($kind, $name, $value);
}

sub select_html {
    my ($kind, $name, $value) = @_;
    my @options = @{ $OPTIONS{$name} };
    my ($main, $extra) = $SUBFIELD{ substr($name, 1) } ? split(/\|/, $value, 2) : ($value);
    $main //= '';
    my @chosen = $kind eq 'multi' ? grep { $_ ne '' } split(/;\s*/, $main) : ($main ne '' ? ($main) : ());
    my %known = map { $_ => 1 } @options;
    my @other = grep { !$known{$_} } @chosen;
    my %selected = map { $_ => 1 } grep { $known{$_} } @chosen;
    $selected{$OTHER} = 1 if @other;
    $selected{$MAKE_CHOICE} = 1 if !%selected;
    my $html = qq{<select name="$name" size="} . ($kind eq 'multi' ? 5 : 1) . qq{"} . ($kind eq 'multi' ? ' MULTIPLE' : '') . qq{ id="id_menu_$name">\n};
    for my $option (@options) {
        $html .= qq{<option value="} . attr($option) . qq{"} . ($selected{$option} ? ' SELECTED' : '') . qq{> $option\n};
    }
    $html .= "</select>\n";
    $html .= qq{<span> <strong>namelijk:</strong> <input type="text" name="${name}b" size="20" value="} . attr(join('; ', @other)) . qq{"></span>\n};
    return $html;
}

# ---- opslaan ----

sub save_record {
    my ($posts) = @_;
    my ($user, $level, $state) = current_user();
    return session_expired() if $state ne 'ok';
    my %values;
    push(@{ $values{ $_->[0] } }, $_->[1]) for @$posts;
    my $first = sub { my $v = $values{ $_[0] }; return $v ? $v->[0] : ''; };

    my $number = pad($first->('ident'));
    for my $post (@$posts) {
        next unless $post->[0] =~ /^a\d+[bx]?$/;
        if ($post->[1] =~ /CHAR\(|UNION|\bSELECT\b|name_const|select\*from|benchmark|\bCHAR\b|CONCAT|\bFROM\b|\bNULL|\bTHEN\(|\bonload\b|\bonclick\b|\bonerror\b|\bonfocus\b|\bonselect\b|\bonsubmit\b|permit\.lst|objecten\.pl|cfg-cbs\d*\.pl/i) {
            page_raw('<html><head></head><body>==Sorry:4==</body></html>');
            return;
        }
    }
    my %records = records();
    my $old = $records{$number};
    return not_available($number) unless $old && may_edit($level);
    my ($minutes, $by) = locked_by_other($number, $user);
    if (defined $minutes) { locked_page($number, $minutes, $by); return; }

    my @built = rebuild(\%values, $old, $number);
    my @stored = @built[0 .. 32];
    my $unchanged = 1;
    for my $i (0 .. 31) {
        $unchanged = 0 if normalize($i, $stored[$i]) ne normalize($i, $old->[$i]);
    }
    if (!$unchanged) {
        $stored[33] = $old->[33];
        $stored[34] = stamp($user);
        append_record(\@stored);
    }
    page($stored[5], "<table><tr><th>Documentnummer</th><td>$number</td></tr></table>");
}

# Bouwt het record op uit de velden, op dezelfde manier als de echte ZCBS (zie RecordRebuilder in de app).
sub rebuild {
    my ($values, $old, $number) = @_;
    my @a = ('', '', '');
    for my $i (3 .. 31) {
        my $list = $values->{"a$i"};
        $a[$i] = $list ? join("\0", @$list) : '';
    }
    for my $i (3 .. 31) {
        my $b = $values->{"a${i}b"} ? $values->{"a${i}b"}[0] : '';
        if ($b =~ /\w/) { my $o = quotemeta($OTHER); $a[$i] =~ s/$o/$b/; }
    }
    for my $i (keys %SUBFIELD) {
        my $x = $values->{"a${i}x"} ? $values->{"a${i}x"}[0] : '';
        $a[$i] .= "|$x" if $x =~ /\w/;
    }
    for my $i (3 .. 31) { $a[$i] =~ s/\t/ /g; }
    my $a32 = $values->{a32} ? $values->{a32}[0] : '';
    my $t = join("\t", $old->[0], $old->[1], $number, @a[3 .. 31], $a32, '', '');
    $t =~ s/\0 *\t/\t/g;
    $t =~ s/\0/; /g;
    $t =~ s/\n/<br>/g;
    $t =~ s/\r/ /g;
    $t =~ s/ +/ /g;
    $t =~ s/ +\t/\t/g;
    $t =~ s/\t[; ]+/\t/g;
    $t =~ s/== Nog onbekend ==//g;
    $t =~ s/\Q$MAKE_CHOICE\E[; ]+//g;
    $t =~ s/[; ]+\Q$MAKE_CHOICE\E//g;
    $t =~ s/\Q$MAKE_CHOICE\E//g;
    $t =~ s/\Q$OTHER\E//g;
    $t =~ s/&quot;/"/g;
    return split(/\t/, $t, -1);
}

sub normalize {
    my ($i, $value) = @_;
    $value //= '';
    $value =~ s/ *<br> */<br>/g;
    $value = '' if $i == 21 && $value eq '0';
    return $value;
}

# ---- hulpjes ----

sub pad {
    my ($number) = @_;
    return $number if $number !~ /^\d+$/;
    return sprintf("%0${WIDTH}d", $number);
}

sub parse_urlencoded {
    my ($text) = @_;
    my %values;
    for my $pair (split(/&/, $text)) {
        my ($key, $value) = split(/=/, $pair, 2);
        next unless defined $key;
        $values{ decode_url($key) } = decode_url($value // '');
    }
    return %values;
}

sub decode_url {
    my ($s) = @_;
    $s =~ tr/+/ /;
    $s =~ s/%([0-9A-Fa-f]{2})/chr(hex($1))/ge;
    return $s;
}

sub parse_multipart {
    my ($body, $boundary) = @_;
    my @posts;
    for my $part (split(/--\Q$boundary\E/, $body)) {
        next unless $part =~ /^\r\n/;
        my ($head, $value) = $part =~ /^\r\n(.*?)\r\n\r\n(.*)\r\n$/s;
        next unless defined $head && $head =~ /name="([^"]*)"/;
        push(@posts, [$1, $value]);
    }
    return \@posts;
}

main();
