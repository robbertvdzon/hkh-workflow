#!/bin/sh
# Nep-/usr/sbin/sendmail voor de lokale ZCBS: verstuurt niets, zet de mail in mailbox.txt.
{
  echo "===== $(date) ====="
  cat
  echo
} >> /home/hkhadmin/domains/historischekringheemskerk.nl/public_html/mailbox.txt
exit 0
