<img src="https://i.imgur.com/QuR0Myp.png" width="200"/>

# android-neutrino

Android mobile wallet using the bchwallet/neutrino backend

## Installing

Neutrino isn't on Google Play. It needs Android 7.0 or newer; download the APK for your phone from
the [releases](https://github.com/gcash/android-neutrino/releases):

- `arm64-v8a` for almost every phone from the last several years
- `armeabi-v7a` for older 32-bit phones
- `x86_64` for emulators and x86 Chromebooks
- `universal` if you aren't sure, which works on all of them at about twice the size

Android asks you to allow installs from the app you open the APK with, such as your browser. You
can check the download against the release's `SHA256SUMS`, and its signature against the
certificate fingerprint in the release notes:

```bash
sha256sum --check --ignore-missing SHA256SUMS
apksigner verify --print-certs neutrino-*.apk
```

The Neutrino that was on Google Play was signed with a different key, so Android won't install
this one over it. Write down its recovery phrase, uninstall it, install this one and restore the
wallet from the phrase.

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

## Releasing

Releases are signed with a key kept outside the repo. Android installs an update only if it is
signed with the same key as the installed app, so keep a backup of the keystore and its password.
Create it once:

```bash
keytool -genkeypair -keystore ~/neutrino-release.jks -alias neutrino \
    -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=Neutrino"
```

Set the version in `app/build.gradle`, then build and sign the release:

```bash
scripts/build-release.sh ~/neutrino-release.jks
```

This asks for the keystore's password and writes an APK for each ABI, a universal APK and
`SHA256SUMS` to `build/release/<version>/`. Attach them to the GitHub release, and give the
certificate's SHA-256 digest, which the script prints, in the release notes.
