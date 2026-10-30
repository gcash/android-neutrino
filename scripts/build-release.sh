#!/usr/bin/env bash
#
# Builds the release APKs, one for each ABI and a universal one, signs them
# with the release key and writes them with their SHA-256 sums to
# build/release/<version>/, ready to attach to a GitHub release.
#
# The keystore stays outside the repo, and the script asks for its password,
# which only keytool and apksigner see. Set ANDROID_HOME; the newest
# build-tools there provide zipalign and apksigner.
#
# Usage: scripts/build-release.sh KEYSTORE [KEY_ALIAS]
set -euo pipefail
if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Usage: $0 KEYSTORE [KEY_ALIAS]" >&2
    exit 1
fi
KEYSTORE=$1
ALIAS=${2:-neutrino}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
if [ ! -f "$KEYSTORE" ]; then
    echo "No keystore at $KEYSTORE" >&2
    exit 1
fi
KEYSTORE=$(cd "$(dirname "$KEYSTORE")" && pwd)/$(basename "$KEYSTORE")
if [ -z "${ANDROID_HOME:-}" ]; then
    echo "ANDROID_HOME is not set" >&2
    exit 1
fi
TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -n 1)
if [ -z "$TOOLS" ] || [ ! -x "$TOOLS/apksigner" ] || [ ! -x "$TOOLS/zipalign" ]; then
    echo "No build-tools with apksigner and zipalign in $ANDROID_HOME/build-tools" >&2
    exit 1
fi
# apksigner loads a native library, which Java 24 and newer warn about on every
# run unless native access is enabled. Its wrapper passes -J options on to
# java, and Java 17 and newer accept this one.
apksigner() {
    "$TOOLS/apksigner" -J-enable-native-access=ALL-UNNAMED "$@"
}
KEYTOOL=${JAVA_HOME:+$JAVA_HOME/bin/}keytool
VERSION=$(sed -n 's/^ *versionName = "\(.*\)"$/\1/p' "$ROOT/app/build.gradle")
if [ -z "$VERSION" ]; then
    echo "No versionName in app/build.gradle" >&2
    exit 1
fi
OUT="$ROOT/build/release/$VERSION"

# Ask for the password first and check it with the key's alias, so that a
# typo doesn't fail the signing after the build. It isn't exported, so the
# build doesn't see it.
read -r -s -p "Password of $KEYSTORE: " PASSWORD
echo
if ! KS_PASS=$PASSWORD "$KEYTOOL" -list -keystore "$KEYSTORE" -alias "$ALIAS" \
    -storepass:env KS_PASS > /dev/null; then
    echo "Can't open the key $ALIAS in $KEYSTORE with that password" >&2
    exit 1
fi

# Build the app from scratch, so that the release depends on nothing left from
# an earlier build. Only the app module is cleaned, since the project's clean
# also deletes build/, and with it the earlier releases in build/release/.
# Gradle prints only errors, and in a terminal its progress bar.
APKS="$ROOT/app/build/outputs/apk/release"
rm -rf "$OUT"
echo "Building $VERSION..."
(cd "$ROOT" && ./gradlew --quiet :app:clean assembleRelease -PabiSplits)
mkdir -p "$OUT"

echo "Signing..."
for ABI in arm64-v8a armeabi-v7a x86_64 universal; do
    UNSIGNED="$APKS/app-$ABI-release-unsigned.apk"
    SIGNED="$OUT/neutrino-$VERSION-$ABI.apk"
    # The native libraries must stay aligned to 16 KB pages, and signing has
    # to follow the alignment, so check it before signing.
    "$TOOLS/zipalign" -c -P 16 4 "$UNSIGNED"
    KS_PASS=$PASSWORD apksigner sign --ks "$KEYSTORE" \
        --ks-key-alias "$ALIAS" --ks-pass env:KS_PASS --out "$SIGNED" "$UNSIGNED"
    apksigner verify "$SIGNED"
done

(cd "$OUT" && sha256sum -- *.apk > SHA256SUMS)
echo
echo "Signed with:"
apksigner verify --print-certs "$OUT/neutrino-$VERSION-universal.apk" |
    grep -E "certificate (DN|SHA-256 digest)"
echo
echo "Wrote $OUT:"
(cd "$OUT" && ls -l -- *.apk SHA256SUMS)
