# ZCBS (Apache + Perl), sftp (sshd) en ftp (vsftpd) in één container, onder supervisord.
FROM httpd:2.4

RUN apt-get update \
 && apt-get install -y --no-install-recommends perl libwww-perl openssh-server vsftpd supervisor \
 && rm -rf /var/lib/apt/lists/* \
 && echo /usr/sbin/nologin >> /etc/shells \
 && useradd -m -u 1001 -d /home/hkhadmin -s /usr/sbin/nologin hkhadmin \
 && echo 'hkhadmin:hkhtest' | chpasswd \
 # De FTP-gebruiker die het echte images.exe gebruikt (ingebouwd; server ftp.historischekringheemskerk.nl). Zelfde uid, home een niveau boven public_html.
 && useradd --badname -o -u 1001 -g hkhadmin -M -d /home/hkhadmin/domains/historischekringheemskerk.nl -s /usr/sbin/nologin 'ZCBS@historischekringheemskerk.nl' \
 && echo 'ZCBS@historischekringheemskerk.nl:hkhtest' | chpasswd \
 && mkdir -p /home/hkhadmin/domains/historischekringheemskerk.nl/public_html /run/sshd /var/run/vsftpd/empty \
 && chown root:root /home/hkhadmin && chmod 755 /home/hkhadmin

COPY apache.conf /usr/local/apache2/conf/httpd.conf
COPY index.html /usr/local/apache2/welcome/index.html
COPY sshd_config /etc/ssh/sshd_config
COPY vsftpd.conf /etc/vsftpd.conf
COPY supervisord.conf /etc/supervisord-zcbs.conf
COPY fake-sendmail.sh /usr/sbin/sendmail
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /usr/sbin/sendmail /entrypoint.sh

EXPOSE 80 22 21 30000-30009
ENTRYPOINT ["/entrypoint.sh"]
