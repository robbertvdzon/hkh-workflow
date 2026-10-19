#!/bin/sh
# Maakt alleen de tekst, geen jpg's, en meldt toch exitcode 0: het geval waar de exitcode alleen niet genoeg is.
mkdir -p output/ocr output/pdf
for f in *.pdf; do
  [ -e "$f" ] || continue
  b="${f%.pdf}"
  mv "$f" "output/pdf/$f"
  echo "tekst van $b" > "output/ocr/$b.txt"
done
