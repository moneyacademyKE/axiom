#!/usr/bin/env bash
# Flaky agent: generates a level on calls 1-2 and 5+, stalls on calls 3-4.
# Uses a gitignored call counter so rollback does NOT reset its phase --
# after a rollback to last-good, the counter still reads "3", so the next
# call is 4 (still stall), then 5 (generates) -> recovery + goal fulfilled.
set -euo pipefail
cd "$(dirname "$0")"
n=$(cat calls.txt 2>/dev/null || echo 0); n=$((n+1)); echo "$n" > calls.txt
touch .axiom-marker
if { [ "$n" -ge 1 ] && [ "$n" -le 2 ]; } || [ "$n" -ge 5 ]; then
  touch "level_${n}.txt"
  git add -A; git commit -q -m "level $n"
fi
