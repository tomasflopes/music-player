#!/bin/sh
# Read-only report of what the dev machine still needs. Exit 1 if anything is missing.
cd "$(dirname "$0")/.."
missing=0
ok()   { printf '  [ok]      %s\n' "$1"; }
miss() { printf '  [MISSING] %s\n' "$1"; missing=1; }

echo "Toolchain"
if /usr/libexec/java_home -v 21 >/dev/null 2>&1 || [ -n "$(ls -d "$HOME"/.sdkman/candidates/java/21* 2>/dev/null)" ]; then
  ok "JDK 21 (used by Gradle; .java-version pins it for jenv)"
else miss "JDK 21  ->  brew install openjdk@21"; fi
command -v git >/dev/null && ok "git" || miss "git"

echo "Android"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [ -d "$SDK" ]; then ok "Android SDK at $SDK"; else miss "Android SDK (install Android Studio and run its first-run wizard)"; fi
ls -d "$SDK"/platforms/android-37* >/dev/null 2>&1 && ok "SDK Platform 37 (Android 17)" || miss "SDK Platform 37 (Android 17): Studio > SDK Manager > SDK Platforms"
[ -x "$SDK/platform-tools/adb" ] && ok "adb" || miss "platform-tools (adb): Studio > SDK Manager > SDK Tools"
[ -x "$SDK/emulator/emulator" ] && ok "emulator" || miss "Android Emulator: Studio > SDK Manager > SDK Tools"
if ls "$SDK"/system-images/android-37*/*/arm64-v8a >/dev/null 2>&1; then ok "Android 17 arm64 system image"
else miss "Android 17 arm64-v8a system image: Studio > SDK Manager > SDK Platforms > Show Package Details"; fi
if [ -x "$SDK/emulator/emulator" ] && "$SDK/emulator/emulator" -list-avds 2>/dev/null | grep -q .; then ok "AVD(s): $("$SDK/emulator/emulator" -list-avds | tr '\n' ' ')"
else miss "an AVD: Studio > Device Manager > Create Device (Android 17 image)"; fi

echo "Repo"
[ "$(git config core.hooksPath)" = ".githooks" ] && ok "pre-push hook enabled" || miss "pre-push hook: run scripts/setup.sh"

[ "$missing" -eq 0 ] && echo "All good." || echo "Some items are missing (see above)."
exit "$missing"
