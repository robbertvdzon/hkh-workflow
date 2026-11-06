# De nagebouwde ZCBS voor de integratietests: Apache + Perl (cgi-bin/archief.pl) en ftp (vsftpd), in één container onder supervisord.
# Dezelfde opzet als de lokale test-ZCBS in tools/docker/zcbs, maar met alles in het image, zodat elke run schoon begint.
FROM httpd:2.4

RUN apt-get update \
 && apt-get install -y --no-install-recommends perl libwww-perl vsftpd supervisor \
 && rm -rf /var/lib/apt/lists/* \
 && echo /usr/sbin/nologin >> /etc/shells \
 && useradd -m -u 1001 -d /home/hkhadmin -s /usr/sbin/nologin hkhadmin \
 && echo 'hkhadmin:hkhtest' | chpasswd \
 # De FTP-gebruiker die het echte images.exe gebruikt (ingebouwd; server ftp.historischekringheemskerk.nl). Zelfde uid, home een niveau boven public_html.
 && useradd --badname -o -u 1001 -g hkhadmin -M -d /home/hkhadmin/domains/historischekringheemskerk.nl -s /usr/sbin/nologin 'ZCBS@historischekringheemskerk.nl' \
 && echo 'ZCBS@historischekringheemskerk.nl:hkhtest' | chpasswd \
 && mkdir -p /home/hkhadmin/domains/historischekringheemskerk.nl/public_html /var/run/vsftpd/empty \
 && chown root:root /home/hkhadmin && chmod 755 /home/hkhadmin

COPY apache.conf /usr/local/apache2/conf/httpd.conf
COPY index.html /usr/local/apache2/welcome/index.html
COPY vsftpd.conf /etc/vsftpd.conf
COPY supervisord.conf /etc/supervisord-zcbs.conf
COPY fake-sendmail.sh /usr/sbin/sendmail
COPY entrypoint.sh /entrypoint.sh
COPY zcbs-reset.sh /usr/local/bin/zcbs-reset
# De beginstand van de ZCBS (scripts, 2 testitems, testgebruikers) zit in het image; zcbs-reset zet hem terug in public_html.
COPY public_html /baseline
RUN mkdir -p /baseline/archief/download \
      /baseline/cgi-bin/archief/databases /baseline/cgi-bin/archief/forms /baseline/cgi-bin/archief/uitgeleend \
      /baseline/cgi-bin/archief/100pct-open /baseline/cgi-bin/archief/100pct-closed \
 && chmod +x /usr/sbin/sendmail /entrypoint.sh /usr/local/bin/zcbs-reset

EXPOSE 80 21
ENTRYPOINT ["/entrypoint.sh"]
