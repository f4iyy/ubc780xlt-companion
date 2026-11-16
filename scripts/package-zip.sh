#!/usr/bin/env bash
# Portable zip: the jar, its libraries, the JavaFX jars of every platform and launch scripts.
# Needs a Java 21 runtime on the machine that runs it (no jpackage, no installer tooling).
# Output: target/dist-zip/ubc780xlt-companion-<version>.zip
# Usage: scripts/package-zip.sh
set -euo pipefail
cd "$(dirname "$0")/.."

bash ./mvnw -B -DskipTests -Djacoco.skip=true package
VERSION="$(bash ./mvnw -B -q -DforceStdout help:evaluate -Dexpression=project.version)"

NAME="ubc780xlt-companion-${VERSION}"
STAGE="target/zip-stage/${NAME}"
rm -rf target/zip-stage target/dist-zip
mkdir -p "$STAGE/lib" "$STAGE/javafx" target/dist-zip

cp target/jpackage-input/ubc780xlt-companion.jar "$STAGE/"
cp target/jpackage-input/lib/*.jar "$STAGE/lib/"

# JavaFX jars are platform specific: fetch each platform's, the launch scripts pick the right folder.
for platform in win linux mac mac-aarch64; do
  bash ./mvnw -B -q dependency:copy-dependencies \
    -Djavafx.platform="$platform" -DincludeScope=runtime \
    -DincludeGroupIds=org.openjfx -DincludeClassifiers="$platform" \
    -DoutputDirectory="$STAGE/javafx/$platform"
done

cp scripts/portable/run.sh scripts/portable/run.bat "$STAGE/"
chmod +x "$STAGE/run.sh"

OUT="$PWD/target/dist-zip/${NAME}.zip"
if command -v zip >/dev/null 2>&1; then
  (cd target/zip-stage && zip -q -r "$OUT" "$NAME")
else
  # No zip tool (Git Bash on Windows): the JDK's jar tool writes a plain zip (run.sh loses its exec bit).
  jar --create --no-manifest --file "$OUT" -C target/zip-stage "$NAME"
fi

echo "Zip OK: target/dist-zip/${NAME}.zip"
