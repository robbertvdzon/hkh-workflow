#!/bin/sh
# Neemt in de integratietests de plaats in van pdf2zcbs.exe: per pdf in de map een tekst, de pdf zelf, small en large in output.
# Een scan met "kapot" in de naam levert geen afbeeldingen, en het programma meldt toch exitcode 0, net als de echte bij een fout. Met "ongeldig" zijn de afbeeldingen er wel, maar is het geen jpg.
# Met "foutcode" stopt het programma met een foutcode. Met "vastloper" wacht het op een hulpprogramma dat niets meer doet, zoals de echte op i_view32.exe met een foutvenster.
rm -rf output
mkdir -p output/ocr output/pdf output/small output/large output/log
for f in *.pdf; do
  [ -e "$f" ] || continue
  b="${f%.pdf}"
  mv "$f" "output/pdf/$f"
  if grep -q foutcode "output/pdf/$f"; then
    echo "Fout: $f is niet te lezen"
    exit 3
  fi
  if grep -q vastloper "output/pdf/$f"; then
    echo "Verwerking: $f : bevat 0 zinnen"
    # macOS stopt een kopie van een systeemprogramma meteen; Linux toont bij een symlink de echte naam.
    if [ "$(uname)" = Darwin ]; then ln -sf "$(command -v sleep)" i_view32; else cp "$(command -v sleep)" i_view32; fi
    ./i_view32 600
    exit 0
  fi
  echo "Tekst uit scan $b" > "output/ocr/$b.txt"
  if grep -q ongeldig "output/pdf/$f"; then
    echo "geen plaatje" > "output/small/$b.jpg"
    echo "geen plaatje" > "output/large/$b.jpg"
  elif ! grep -q kapot "output/pdf/$f"; then
    cp sample.bin "output/small/$b.jpg"
    cp sample.bin "output/large/$b.jpg"
  fi
done
echo klaar > output/log/log.txt
