#!/bin/sh
# Nep-/usr/sbin/sendmail voor de lokale ZCBS: verstuurt niets, zet de mail in mailbox.txt.
{
  echo "===== $(date) ====="
  cat
  echo
} >> /usr/local/apache2/htdocs/mailbox.txt
exit 0
