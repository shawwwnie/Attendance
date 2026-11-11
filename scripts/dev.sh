#!/usr/bin/env bash
set -euo pipefail
CAREFLOW_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CAREFLOW_API_PID=""
cleanup() {
  if [[ -n "$CAREFLOW_API_PID" ]]; then kill "$CAREFLOW_API_PID" 2>/dev/null || true; fi
}
trap cleanup EXIT INT TERM
cd "$CAREFLOW_ROOT/backend"
./mvnw -B -ntp test-compile dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt
java -cp "target/test-classes:target/classes:$(cat target/test-classpath.txt)" dev.careflow.LocalDemoServer &
CAREFLOW_API_PID=$!
cd "$CAREFLOW_ROOT/frontend"
if [[ ! -d node_modules ]]; then npm ci; fi
printf '\nCareFlow frontend: http://localhost:5173\n데모 계정: manager / careflow-demo\n임시 DB는 종료 시 삭제됩니다.\n\n'
npm run dev
