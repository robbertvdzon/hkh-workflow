#!/bin/sh
set -e
# Vaste sleutels voor sftp in een volume, zodat de vingerafdruk niet verandert als de container opnieuw wordt gemaakt.
mkdir -p /etc/ssh/keys /run/sshd /var/run/vsftpd/empty
for t in ed25519 rsa; do
  [ -f /etc/ssh/keys/ssh_host_${t}_key ] || ssh-keygen -q -t "$t" -N '' -f /etc/ssh/keys/ssh_host_${t}_key
done
touch /var/log/vsftpd.log

# Het adres dat ftp aan clients doorgeeft voor passieve verbindingen (zie README, "Vanaf een andere computer").
echo "pasv_address=${PASV_ADDRESS:-127.0.0.1}" >> /etc/vsftpd.conf

# Mappen die ZCBS, ftp en sftp verwachten (lege mappen blijven niet altijd bestaan als je files/ kopieert of in git zet).
R=/home/hkhadmin/domains/historischekringheemskerk.nl/public_html
for d in cgi-bin/archief/addenda cgi-bin/archief/backup1 cgi-bin/archief/databases cgi-bin/archief/forms cgi-bin/archief/uitgeleend \
         cgi-bin/archief/100pct cgi-bin/archief/100pct-open cgi-bin/archief/100pct-closed cgi-bin/archief/misc \
         archief/large archief/small archief/ocr archief/pdf archief/logging/extern archief/download archief/misc; do
  mkdir -p -m 777 "$R/$d"
done
exec /usr/bin/supervisord -n -c /etc/supervisord-zcbs.conf
