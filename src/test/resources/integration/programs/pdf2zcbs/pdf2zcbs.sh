#!/bin/sh
# Neemt in de integratietests de plaats in van pdf2zcbs.exe: per pdf in de map een tekst, de pdf zelf, small en large in output.
# Een scan met "kapot" in de naam levert geen afbeeldingen, en het programma meldt toch exitcode 0, net als de echte bij een fout.
rm -rf output
mkdir -p output/ocr output/pdf output/small output/large output/log
for f in *.pdf; do
  [ -e "$f" ] || continue
  b="${f%.pdf}"
  mv "$f" "output/pdf/$f"
  echo "Tekst uit scan $b" > "output/ocr/$b.txt"
  if ! grep -q kapot "output/pdf/$f"; then
    cp sample.bin "output/small/$b.jpg"
    cp sample.bin "output/large/$b.jpg"
  fi
done
echo klaar > output/log/log.txt
