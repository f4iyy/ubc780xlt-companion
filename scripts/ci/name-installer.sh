#!/usr/bin/env bash
# Gives the installer built by scripts/package a name that carries the commit:
#   ubc780xlt-companion-<first 7 characters of the commit>.<extension>
# or, when INSTALLER_VERSION is set (releases), the version:
#   ubc780xlt-companion-<version>.<extension>
# There is exactly one file in target/dist (an installer); it is renamed in place.
# Writes "path=<new path>" to $GITHUB_OUTPUT when run by GitHub Actions.
#
# Usage: COMMIT_SHA=<full sha> [INSTALLER_VERSION=<version>] scripts/ci/name-installer.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

: "${COMMIT_SHA:?COMMIT_SHA must be set}"
SUFFIX="${INSTALLER_VERSION:-${COMMIT_SHA:0:7}}"

shopt -s nullglob
files=(target/dist/*)
if [ "${#files[@]}" -ne 1 ] || [ ! -f "${files[0]}" ]; then
  echo "Expected exactly one installer file in target/dist, found: ${files[*]:-nothing}" >&2
  exit 1
fi

file="${files[0]}"
extension="${file##*.}"
renamed="target/dist/ubc780xlt-companion-${SUFFIX}.${extension}"
mv "$file" "$renamed"
echo "Installer: $renamed"

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "path=$renamed" >> "$GITHUB_OUTPUT"
fi
