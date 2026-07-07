#!/usr/bin/env bash
# Steady agent: appends one "level" line to the state file per call.
# Yields strictly monotonic progress (level-count increments), so the loop
# never stalls and eventually meets a `level-count >= N` goal.
#
#   Usage: generate.sh <state-file> <attempt-index>
set -euo pipefail
STATE="${1:?state file required}"
ATTEMPT="${2:-0}"
mkdir -p "$(dirname "$STATE")"
echo "level ${ATTEMPT}" >> "$STATE"
