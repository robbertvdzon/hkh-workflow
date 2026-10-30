#!/bin/sh
# Nep-pdf2zcbs.exe voor een scan waar geen tekst in gevonden wordt: de tekst is leeg, de rest is er wel.
mkdir -p output/ocr output/pdf output/small output/large output/log
for f in *.pdf; do
  [ -e "$f" ] || continue
  b="${f%.pdf}"
  mv "$f" "output/pdf/$f"
  : > "output/ocr/$b.txt"
  echo "small $b" > "output/small/$b.jpg"
  echo "large $b" > "output/large/$b.jpg"
done
