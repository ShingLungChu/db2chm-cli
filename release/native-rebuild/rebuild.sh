#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
compiler="${FPC:-fpc}"
test "$("$compiler" -iV)" = 3.2.2
out="${1:-build}"
if [[ -e "$out" ]]; then echo 'Choose a fresh output directory' >&2; exit 1; fi
mkdir -p "$out/units"
out="$(cd "$out" && pwd)"
# Sources already contain the compatibility/encoding patches. Do not apply them again.
"$compiler" -B -O2 -Xs -viu "-Fu$PWD/packages/chm/src" "-FU$out/units" "-FE$out" "$PWD/packages/chm/src/chmcmd.lpr" 2>&1 | tee "$out/build.log"
