#!/usr/bin/env bash
# Build the runtime image and the installer for the current platform with jpackage.
# Output: target/dist/ (one file per installer, never zipped).
# Usage: scripts/package.sh            (installer: dmg on macOS, deb on Linux, msi on Windows)
#        PACKAGE_TYPE=app-image scripts/package.sh   (portable folder, no installer tooling needed)
set -euo pipefail
cd "$(dirname "$0")/.."

bash ./mvnw -B -DskipTests -Djacoco.skip=true package

VERSION="$(bash ./mvnw -B -q -DforceStdout help:evaluate -Dexpression=project.version)"
APP_VERSION="${VERSION%-SNAPSHOT}"
NAME="UBC780XLT Companion"
MAIN_CLASS="io.github.f4iyy.ubc780xlt.Launcher"
MAIN_JAR="ubc780xlt-companion.jar"

case "$(uname -s)" in
  Linux*)  DEFAULT_TYPE="deb";  EXTRA=(--linux-shortcut) ;;
  Darwin*) DEFAULT_TYPE="dmg";  EXTRA=() ;;
  MINGW*|MSYS*|CYGWIN*) DEFAULT_TYPE="msi"; EXTRA=(--win-menu --win-shortcut --win-dir-chooser) ;;
  *) echo "Unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac
TYPE="${PACKAGE_TYPE:-$DEFAULT_TYPE}"

# macOS jpackage refuses a version whose first number is 0 (0.1.0 becomes 1.1.0 there).
if [ "$(uname -s)" = "Darwin" ] && [ "${APP_VERSION%%.*}" = "0" ]; then
  APP_VERSION="1.${APP_VERSION#0.}"
fi
if [ "$TYPE" = "app-image" ]; then EXTRA=(); fi

JPACKAGE="jpackage"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jpackage" ]; then JPACKAGE="$JAVA_HOME/bin/jpackage"; fi

# A JDK that already contains JavaFX (Zulu FX, Liberica Full...) cannot be mixed with the JavaFX jars of Maven:
# jlink rejects them. Use the JavaFX of the JDK then; a plain JDK (Temurin, as on CI) gets the Maven jars.
JAVA="java"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA="$JAVA_HOME/bin/java"; fi
if "$JAVA" --list-modules | grep -q '^javafx.controls'; then
  echo "The JDK contains JavaFX: using it instead of the Maven jars."
  MODULE_PATH=()
else
  MODULE_PATH=(--module-path target/jpackage-modules)
fi

rm -rf target/dist
mkdir -p target/dist

"$JPACKAGE" \
  --type "$TYPE" \
  --name "$NAME" \
  --app-version "$APP_VERSION" \
  --vendor "f4iyy" \
  --input target/jpackage-input \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  ${MODULE_PATH[@]+"${MODULE_PATH[@]}"} \
  --add-modules javafx.controls,java.logging,java.prefs,java.xml,java.management,java.naming,java.sql,java.desktop,jdk.unsupported \
  --dest target/dist \
  ${EXTRA[@]+"${EXTRA[@]}"}

echo
echo "Package OK:"
ls -1 target/dist
