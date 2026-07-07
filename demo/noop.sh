#!/usr/bin/env bash
# Stall fake agent: does nothing and exits 0.
# The counter it is measured against never advances, so every iteration
# is a no-op -- the loop burns through its stall budget and halts.
set -euo pipefail
echo "noop"
