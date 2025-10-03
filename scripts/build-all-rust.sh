#!/usr/bin/env bash
set -euo pipefail

# Cross-compile JNI library for macOS, Linux, and Windows from macOS.
# Prereqs (one time):
#   rustup target add x86_64-unknown-linux-gnu x86_64-pc-windows-gnu
#   brew install zig
#   cargo install cargo-zigbuild
#
# Static SQLite helps avoid missing libsqlite on target hosts used by timsrust via rusqlite.
export LIBSQLITE3_SYS_BUNDLED=1
export RUSQLITE_STATIC=1

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CRATE_DIR="$ROOT/rust-jni"
OUT_DIR="$ROOT/target/classes/META-INF/lib"

mkdir -p "$OUT_DIR/osx-aarch64" "$OUT_DIR/linux-x86_64" "$OUT_DIR/windows-x86_64"

echo ">> Crate: $CRATE_DIR"
echo ">> Out: $OUT_DIR"

mkdir -p "$OUT_DIR"

echo ">> Building macOS (aarch64) dylib"
cargo build --manifest-path "$CRATE_DIR/Cargo.toml" --release
cp "$CRATE_DIR/target/release/libtimsrust_jni.dylib" "$OUT_DIR/osx-aarch64/"

echo ">> Building Linux x86_64 .so with zig"
cargo zigbuild --manifest-path "$CRATE_DIR/Cargo.toml" --release \
  --target x86_64-unknown-linux-gnu
cp "$CRATE_DIR/target/x86_64-unknown-linux-gnu/release/libtimsrust_jni.so" \
   "$OUT_DIR/linux-x86_64/"

echo ">> Building Windows x86_64 .dll with zig"
cargo zigbuild --manifest-path "$CRATE_DIR/Cargo.toml" --release \
  --target x86_64-pc-windows-gnu
cp "$CRATE_DIR/target/x86_64-pc-windows-gnu/release/timsrust_jni.dll" \
   "$OUT_DIR/windows-x86_64/"

echo "Done. Staged natives in $OUT_DIR:"
find "$OUT_DIR" -type f -maxdepth 2 -print
