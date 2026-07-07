#!/usr/bin/env bash
# Corrupting agent: removes the integrity marker. Drives the integrity-halt path.
set -euo pipefail
cd "$(dirname "$0")"
rm -f .axiom-marker
echo "corrupted integrity marker (attempt $1)"
