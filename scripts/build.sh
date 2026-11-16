#!/usr/bin/env bash
# Compile the application (no tests). Usage: scripts/build.sh
set -euo pipefail
cd "$(dirname "$0")/.."

bash ./mvnw -B clean compile

echo "Build OK: target/classes"
