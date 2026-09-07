#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Pravi staticke instance Rubik-a i Nunito-a iz varijabilnih OFL izvora.
#
# Zasto staticke, a ne varijabilni fajl direktno:
#   - PDF izvoz ugradjuje Rubik kao CIDFontType2. Varijabilni TTF nosi 'gvar' delte
#     koje citac PDF-a ne interpretira, pa bi svaka tezina izgledala kao podrazumevana.
#     Instanca je jedini nacin da SemiBold u PDF-u zaista bude SemiBold.
#   - Ista cetiri fajla onda posluze i Compose-u, pa font postoji u jednoj kopiji.
#
# Trazi: python sa fonttools (pip install fonttools), curl.
# Pokrenuti iz korena repoa:  bash scripts/build-fonts.sh
set -euo pipefail

OUT="app/src/main/assets/fonts"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

base="https://raw.githubusercontent.com/google/fonts/main/ofl"

fetch() { curl -sSfL "$1" -o "$2"; }

echo "Preuzimanje varijabilnih izvora..."
fetch "$base/rubik/Rubik%5Bwght%5D.ttf"   "$TMP/Rubik.ttf"
fetch "$base/nunito/Nunito%5Bwght%5D.ttf" "$TMP/Nunito.ttf"
fetch "$base/rubik/OFL.txt"   "$OUT/OFL-Rubik.txt"
fetch "$base/nunito/OFL.txt"  "$OUT/OFL-Nunito.txt"

instance() { # <izvor> <tezina> <izlaz>
  python -m fontTools.varLib.instancer "$1" "wght=$2" -o "$3" >/dev/null
  echo "  $(basename "$3")  $(wc -c < "$3") B"
}

echo "Instanciranje Rubik-a..."
instance "$TMP/Rubik.ttf" 400 "$OUT/rubik_regular.ttf"
instance "$TMP/Rubik.ttf" 500 "$OUT/rubik_medium.ttf"
instance "$TMP/Rubik.ttf" 600 "$OUT/rubik_semibold.ttf"
instance "$TMP/Rubik.ttf" 700 "$OUT/rubik_bold.ttf"

echo "Instanciranje Nunito-a..."
instance "$TMP/Nunito.ttf" 400 "$OUT/nunito_regular.ttf"
instance "$TMP/Nunito.ttf" 600 "$OUT/nunito_semibold.ttf"
instance "$TMP/Nunito.ttf" 700 "$OUT/nunito_bold.ttf"

echo "Provera srpske dijakritike (c ć č s š z ž d đ)..."
python - "$OUT" <<'PY'
import sys, pathlib
from fontTools.ttLib import TTFont
need = "čćšžđČĆŠŽĐ€"
bad = False
for f in sorted(pathlib.Path(sys.argv[1]).glob("*.ttf")):
    cmap = TTFont(f).getBestCmap()
    missing = [c for c in need if ord(c) not in cmap]
    if missing:
        bad = True
        print(f"  NEDOSTAJE u {f.name}: {''.join(missing)}")
    else:
        print(f"  OK  {f.name}")
sys.exit(1 if bad else 0)
PY

echo "Gotovo."
