#!/bin/sh
set -e
# Vaste sleutels voor sftp in een volume, zodat de vingerafdruk niet verandert als de container opnieuw wordt gemaakt.
mkdir -p /etc/ssh/keys /run/sshd /var/run/vsftpd/empty
for t in ed25519 rsa; do
  [ -f /etc/ssh/keys/ssh_host_${t}_key ] || ssh-keygen -q -t "$t" -N '' -f /etc/ssh/keys/ssh_host_${t}_key
done
touch /var/log/vsftpd.log

# Na een herstart van de container (of van de computer) kan de socket van de CGI-daemon van de vorige run nog in de schrijflaag staan.
# Apache kan er dan niet aan binden ("Address already in use") en alle scripts geven een 503. Dus altijd eerst opruimen.
rm -f /usr/local/apache2/logs/cgisock.* /usr/local/apache2/logs/httpd.pid

# Het adres dat ftp aan clients doorgeeft voor passieve verbindingen (zie README, "Vanaf een andere computer").
echo "pasv_address=${PASV_ADDRESS:-127.0.0.1}" >> /etc/vsftpd.conf

exec /usr/bin/supervisord -n -c /etc/supervisord-zcbs.conf
