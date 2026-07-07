#!/usr/bin/env bash
# Reset the recover demo to a clean known-good git baseline.
# Recreates the integrity marker, clears generated levels, and commits a
# fresh baseline so `git/tag!` (last-good) and `git/rollback!` are meaningful.
set -euo pipefail
cd "$(dirname "$0")"
rm -f level_*.txt calls.txt
echo "GOOD" > .axiom-marker
git rm -q --cached -r .axiom-logs-recover 2>/dev/null || true
git add -A
git commit -q -m "reset baseline" 2>/dev/null || true
echo "reset OK"
