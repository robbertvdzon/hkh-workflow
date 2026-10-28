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

exec /usr/bin/supervisord -n -c /etc/supervisord-zcbs.conf
