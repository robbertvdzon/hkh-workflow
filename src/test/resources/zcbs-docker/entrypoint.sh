#!/bin/sh
set -e
mkdir -p /var/run/vsftpd/empty
touch /var/log/vsftpd.log

# Na een herstart kan de socket van de CGI-daemon van de vorige run nog in de schrijflaag staan.
rm -f /usr/local/apache2/logs/cgisock.* /usr/local/apache2/logs/httpd.pid

# Passieve ftp: het adres en de poorten die de server aan clients doorgeeft. De test kiest vrije poorten op de host en koppelt ze een-op-een.
{
  echo "pasv_address=${PASV_ADDRESS:-127.0.0.1}"
  echo "pasv_min_port=${PASV_MIN_PORT:-30000}"
  echo "pasv_max_port=${PASV_MAX_PORT:-30009}"
} >> /etc/vsftpd.conf

# Altijd starten vanuit de beginstand.
/usr/local/bin/zcbs-reset

exec /usr/bin/supervisord -n -c /etc/supervisord-zcbs.conf
