#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -x "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java" ]]; then
  JAVA21_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
elif [[ -x "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java" ]]; then
  JAVA21_HOME="/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
else
  echo "JDK 21 not found. Install it first:"
  echo "  brew install openjdk@21"
  exit 1
fi

if [[ ! -x "$SCRIPT_DIR/gradlew" ]]; then
  echo "Gradle wrapper not found in $SCRIPT_DIR."
  echo "Run once:"
  echo "  cd \"$SCRIPT_DIR\" && gradle wrapper --gradle-version 9.4.1 && chmod +x gradlew"
  exit 1
fi

(
  export JAVA_HOME="$JAVA21_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
  cd "$SCRIPT_DIR"
  ./gradlew -Dorg.gradle.java.home="$JAVA_HOME" --stop >/dev/null 2>&1 || true
  ./gradlew -Dorg.gradle.java.home="$JAVA_HOME" --no-daemon "$@"
)
