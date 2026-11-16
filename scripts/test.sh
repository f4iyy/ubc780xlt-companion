#!/usr/bin/env bash
# Unit + integration tests, reports and the 80% coverage gate. Usage: scripts/test.sh
set -euo pipefail
cd "$(dirname "$0")/.."

bash ./mvnw -B verify

echo
echo "Test report     : target/reports/tests/tests.html"
echo "Coverage report : target/reports/coverage/index.html"
