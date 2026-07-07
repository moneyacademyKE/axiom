#!/usr/bin/env bash
# Reset the demo workspace between runs.
set -euo pipefail
cd "$(dirname "$0")"
rm -f level_*.txt
rm -f .axiom-marker .axiom.lock
rm -rf .axiom-logs-*
echo "axiom-marker" > .axiom-marker
echo "demo workspace reset"
