#!/usr/bin/env bash
#
# Builds bchwallet/bchwallet.aar, the bchwallet daemon the app embeds, from
# github.com/gcash/bchwallet with gomobile.
#
# Requires git, Go, the Android SDK and the Android NDK r28 or newer, whose
# 64-bit libraries have the 16 KB page alignment Google Play requires. Set
# ANDROID_HOME, and ANDROID_NDK_HOME unless the NDK is in $ANDROID_HOME/ndk.
#
# Usage: scripts/build-bchwallet-aar.sh [bchwallet git ref]
#
# Set BCHWALLET_DIR to build from a local checkout instead of cloning.

set -euo pipefail

BCHWALLET_REF=${1:-master}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/bchwallet/bchwallet.aar"

if [ -z "${ANDROID_HOME:-}" ]; then
    echo "ANDROID_HOME is not set" >&2
    exit 1
fi
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    ANDROID_NDK_HOME=$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -n 1)
    if [ -z "$ANDROID_NDK_HOME" ]; then
        echo "No NDK found in $ANDROID_HOME/ndk; set ANDROID_NDK_HOME" >&2
        exit 1
    fi
fi
export ANDROID_HOME ANDROID_NDK_HOME

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

if [ -n "${BCHWALLET_DIR:-}" ]; then
    SRC=$(cd "$BCHWALLET_DIR" && pwd)
else
    SRC="$WORK/bchwallet"
    git clone --quiet https://github.com/gcash/bchwallet "$SRC"
    git -C "$SRC" checkout --quiet "$BCHWALLET_REF"
fi
echo "Building bchwallet $(git -C "$SRC" describe --tags --always --dirty 2>/dev/null || echo "$BCHWALLET_REF") with $(go version)"

# Use the gomobile and gobind versions that bchwallet tracks in its go.mod.
(cd "$SRC" && GOBIN="$WORK/bin" go install golang.org/x/mobile/cmd/gomobile golang.org/x/mobile/cmd/gobind)

mkdir -p "$(dirname "$OUT")"
(cd "$SRC" && PATH="$WORK/bin:$PATH" gomobile bind \
    -target=android/arm64,android/arm,android/amd64 \
    -androidapi 24 \
    -trimpath \
    -ldflags="-s -w" \
    -o "$WORK/bchwallet.aar" \
    ./mobile)

# Check that the 64-bit libraries are aligned for 16 KB pages.
OBJDUMP=$(ls "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin/llvm-objdump | head -n 1)
mkdir -p "$WORK/aar"
unzip -q "$WORK/bchwallet.aar" 'jni/*' -d "$WORK/aar"
for abi in arm64-v8a x86_64; do
    if "$OBJDUMP" -p "$WORK/aar/jni/$abi/libgojni.so" | grep -E '^\s*LOAD' | grep -vq 'align 2\*\*14$'; then
        echo "jni/$abi/libgojni.so is not aligned for 16 KB pages; use NDK r28 or newer" >&2
        exit 1
    fi
done

mv "$WORK/bchwallet.aar" "$OUT"
echo "Wrote $OUT"
