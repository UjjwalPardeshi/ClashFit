#!/usr/bin/env bash
# Build ClashFit and push it to a phone over USB.
#
#   ./install-to-phone.sh            debug build, install, launch
#   ./install-to-phone.sh release    release build (smaller), install, launch
#   ./install-to-phone.sh --apk-only just build, print the path, do not install
#
# The toolchain lives entirely in $HOME; nothing was installed system-wide.
set -euo pipefail
cd "$(dirname "$0")"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export JAVA_HOME="${JAVA_HOME:-$(ls -d "$HOME"/.jdks/jdk-21* 2>/dev/null | head -1)}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
ADB="$ANDROID_HOME/platform-tools/adb"

say() { printf '\n\033[1;38;5;202m%s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m%s\033[0m\n' "$*" >&2; exit 1; }

[ -x "$JAVA_HOME/bin/javac" ] || die "No JDK at $JAVA_HOME. Expected ~/.jdks/jdk-21*"
[ -d "$ANDROID_HOME/platforms" ] || die "No Android SDK at $ANDROID_HOME"

VARIANT="debug"; APK_ONLY=0
for a in "$@"; do
  case "$a" in
    release) VARIANT="release" ;;
    --apk-only) APK_ONLY=1 ;;
  esac
done
TASK=":app:assemble$(tr '[:lower:]' '[:upper:]' <<< "${VARIANT:0:1}")${VARIANT:1}"

# Memory is held down on purpose. The default in gradle.properties is 3g, which
# froze this laptop mid-build once already; 2g and two workers is slower and survives.
say "Building $VARIANT ..."
nice -n 10 ./gradlew "$TASK" --no-daemon --console=plain --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m" \
  -Dkotlin.daemon.jvmargs="-Xmx1400m" \
  -Dorg.gradle.parallel=false

APK=$(find "app/build/outputs/apk/$VARIANT" -name '*.apk' | head -1)
[ -n "$APK" ] || die "No APK produced."
printf '\n  APK  %s\n  size %.1f MB\n' "$APK" "$(stat -c%s "$APK" | awk '{print $1/1048576}')"
[ "$APK_ONLY" = "1" ] && { say "Built. Copy that file to the phone and tap it."; exit 0; }

say "Looking for a phone ..."
"$ADB" start-server >/dev/null 2>&1 || true
for i in $(seq 1 20); do
  STATE=$("$ADB" get-state 2>/dev/null || true)
  [ "$STATE" = "device" ] && break
  if "$ADB" devices | grep -q unauthorized; then
    echo "  Phone seen but NOT authorised. Unlock it and tap 'Allow' on the USB debugging prompt."
  else
    echo "  waiting for a device ... ($i/20)"
  fi
  sleep 3
done
[ "$("$ADB" get-state 2>/dev/null || true)" = "device" ] || die \
"No phone.
  1  Settings > About phone > Software information > tap 'Build number' 7 times
  2  Settings > Developer options > USB debugging  ON
  3  Plug in the cable, then pull down the notification and set USB mode to
     'File transfer' (charging-only will not expose adb)
  4  Tap 'Allow' on the 'Allow USB debugging?' prompt
  Then run this script again."

MODEL=$("$ADB" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
echo "  device: $MODEL"

say "Installing ..."
"$ADB" install -r -d "$APK" || die "Install failed. If it mentions signatures, run: $ADB uninstall com.clashfit"
"$ADB" shell monkey -p com.clashfit -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
say "Installed and launched on $MODEL."
echo "  Grant Camera when it asks. Without the Gemma model file the template coach speaks instead;"
echo "  everything else, including every mode, works."
