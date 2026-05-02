#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEEK03_WRAPPER="$SCRIPT_DIR/../week03/gradlew"

if [[ -x "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java" ]]; then
  JAVA21_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
elif [[ -x "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java" ]]; then
  JAVA21_HOME="/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
else
  echo "JDK 21 not found. Install it first:"
  echo "  brew install openjdk@21"
  exit 1
fi

if [[ ! -x "$WEEK03_WRAPPER" ]]; then
  echo "Gradle wrapper not found at $WEEK03_WRAPPER."
  echo "Expected an existing wrapper in week03."
  exit 1
fi

(
  export JAVA_HOME="$JAVA21_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
  cd "$SCRIPT_DIR"
  "$WEEK03_WRAPPER" -p "$SCRIPT_DIR" -Dorg.gradle.java.home="$JAVA_HOME" --stop >/dev/null 2>&1 || true
  "$WEEK03_WRAPPER" -p "$SCRIPT_DIR" -Dorg.gradle.java.home="$JAVA_HOME" --no-daemon "$@"
)
