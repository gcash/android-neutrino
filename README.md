<img src="https://i.imgur.com/QuR0Myp.png" width="200"/>

# android-neutrino

Android mobile wallet using the bchwallet/neutrino backend

## Building

The app embeds the [bchwallet](https://github.com/gcash/bchwallet) daemon, built for Android
with gomobile, as `bchwallet/bchwallet.aar`.

Requirements:

- JDK 17 or newer, such as the one bundled with Android Studio
- The Android SDK with platform 36
- To build `bchwallet.aar`: git, Go 1.26 or newer and the Android NDK r28 or newer

1. Build the wallet library. This clones bchwallet at the given git ref, `master` by default,
   and writes `bchwallet/bchwallet.aar` with libraries for arm64-v8a, armeabi-v7a and x86_64:

   ```bash
   export ANDROID_HOME=$HOME/Android/Sdk
   scripts/build-bchwallet-aar.sh master
   ```

   Set `BCHWALLET_DIR` to build from a local bchwallet checkout instead.

2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```

Run the unit tests with `./gradlew testDebugUnitTest`.
