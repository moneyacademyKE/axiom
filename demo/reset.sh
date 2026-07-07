#!/usr/bin/env bash
# Reset the demo workspace to a clean state so demos are reproducible.
# Run before each demo run:
#   bash demo/reset.sh
#
# Wipes per-scenario state (levels / build-ok) and prior run logs. Each
# scenario re-seeds its defaults lazily via the observers' `|| echo ...`
# fallbacks, so no explicit seeding is needed here.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf workspace state logs
mkdir -p workspace
echo "demo workspace reset: clean (workspace/, logs/ removed)"
