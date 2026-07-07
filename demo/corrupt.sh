#!/usr/bin/env bash
# Corrupting fake agent: writes an integrity-violating marker.
# The first act poisons the status file; the next observe reads "broken",
# which violates the configured integrity axiom -- the loop halts.
#
# Usage: corrupt.sh <statusfile>
set -euo pipefail
statusfile="${1:?usage: corrupt.sh <statusfile>}"
mkdir -p "$(dirname "$statusfile")"
echo "broken" > "$statusfile"
echo "corrupted: status=broken"
