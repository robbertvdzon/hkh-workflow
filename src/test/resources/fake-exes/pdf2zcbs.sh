#!/bin/sh
# Nep-pdf2zcbs.exe voor de tests: per pdf in de map een tekst, de pdf zelf, small en large, zoals het echte programma.
mkdir -p output/ocr output/pdf output/small output/large output/log
for f in *.pdf; do
  [ -e "$f" ] || continue
  b="${f%.pdf}"
  mv "$f" "output/pdf/$f"
  echo "tekst van $b" > "output/ocr/$b.txt"
  echo "small $b" > "output/small/$b.jpg"
  echo "large $b" > "output/large/$b.jpg"
done
echo log > output/log/log.txt
