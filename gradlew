#!/usr/bin/env sh
set -e

GRADLE_VERSION="8.4"
GRADLE_DIR="$HOME/.gradle-dist/gradle-$GRADLE_VERSION"

if [ ! -d "$GRADLE_DIR" ]; then
  mkdir -p "$HOME/.gradle-dist"
  echo "Downloading Gradle $GRADLE_VERSION..."
  curl -sL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$HOME/.gradle-dist/gradle.zip"
  unzip -q "$HOME/.gradle-dist/gradle.zip" -d "$HOME/.gradle-dist/"
  rm -f "$HOME/.gradle-dist/gradle.zip"
fi

exec "$GRADLE_DIR/bin/gradle" "$@"
