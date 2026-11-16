#!/usr/bin/env sh
# Starts UBC780XLT Companion. Needs a Java 21 runtime (JRE or JDK) on the PATH or in JAVA_HOME.
cd "$(dirname "$0")" || exit 1

case "$(uname -s)" in
  Linux*)  PLATFORM=linux ;;
  Darwin*) if [ "$(uname -m)" = "arm64" ]; then PLATFORM=mac-aarch64; else PLATFORM=mac; fi ;;
  *) echo "Unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

JAVA=java
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA="$JAVA_HOME/bin/java"; fi

exec "$JAVA" \
  --module-path "javafx/$PLATFORM" --add-modules javafx.controls \
  -cp "ubc780xlt-companion.jar:lib/*" \
  io.github.f4iyy.ubc780xlt.Launcher "$@"
