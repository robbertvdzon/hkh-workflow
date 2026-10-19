#!/bin/sh
# Nep-images.exe voor de tests: small, large en 100pct van de jpg's in 'images', en een logbestand.
mkdir -p small large 100pct
for f in images/*.jpg; do
  [ -e "$f" ] || continue
  n="$(basename "$f")"
  cp "$f" "small/$n"; cp "$f" "large/$n"; cp "$f" "100pct/$n"
done
echo log > log-261019-2000.txt
