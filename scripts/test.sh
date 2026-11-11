#!/usr/bin/env bash
set -euo pipefail
CAREFLOW_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$CAREFLOW_ROOT/backend"
./mvnw -B -ntp verify
cd "$CAREFLOW_ROOT/frontend"
npm ci
npm test
npm run build
