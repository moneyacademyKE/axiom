#!/usr/bin/env bash
# Steady agent: creates exactly one new level file per call (monotonic progress).
# Also ensures the integrity marker is present.
set -euo pipefail
cd "$(dirname "$0")"
touch .axiom-marker
touch "level_$(date +%s%N).txt"
echo "generated a level (attempt $1)"
