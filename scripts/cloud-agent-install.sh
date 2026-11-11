#!/usr/bin/env bash
# Idempotent Cloud Agent bootstrap: Android SDK 36 + Gradle caches.
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

CMDLINE_TOOLS_ZIP="commandlinetools-linux-14742923_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"

if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  sudo mkdir -p "$ANDROID_HOME"
  sudo chown "$(id -un):$(id -gn)" "$ANDROID_HOME"
  tmp="$(mktemp -d)"
  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$tmp/$CMDLINE_TOOLS_ZIP"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q "$tmp/$CMDLINE_TOOLS_ZIP" -d "$tmp"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

if [[ ! -x /usr/local/bin/sdkmanager ]]; then
  sudo ln -sf "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" /usr/local/bin/sdkmanager
fi

yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
sdkmanager --sdk_root="$ANDROID_HOME" \
  "platforms;android-36" \
  "build-tools;36.0.0" \
  "build-tools;35.0.0" \
  "platform-tools"

printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

./gradlew --version
./gradlew :core:budget:compileKotlin :core:design:preBuild :app:preBuild --no-daemon
