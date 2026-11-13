# 0002: minSdk, targetSdk and compileSdk are all Android 17 (API 37)

Date: 2026-11-13 · Status: accepted

**Context.** Personal app for one phone, which runs Android 17.

**Decision.** `minSdk = targetSdk = compileSdk = 37`, set once in `build-logic` (`ANDROID_SDK`). The emulator uses an Android 17 image.

**Consequences.** All current media APIs are available and there is no compatibility code. The app will not install on older phones.
