#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.."; pwd)"
PROJ="$ROOT/thermo-raw-server/MSRaw.Thermo.Server.csproj"
OUT_BASE="$ROOT/core/src/main/resources/msraw/thermo/bin"
PUB_BASE="$ROOT/publish"

# Thermo doesn’t ship osx-arm64. Use Rosetta (osx-x64), plus linux-x64 and win-x64.
RIDS=("osx-x64" "linux-x64" "win-x64")

# Fresh staging
rm -rf "$OUT_BASE" "$PUB_BASE"
mkdir -p "$OUT_BASE" "$PUB_BASE"

echo "Publishing self-contained servers..."
for rid in "${RIDS[@]}"; do
  PUB="$PUB_BASE/$rid"
  dotnet publish "$PROJ" -c Release -r "$rid" \
    --self-contained true \
    -o "$PUB" \
    --configfile "$ROOT/NuGet.config"

  if ! ls "$PUB" | grep -q '^ThermoFisher\.CommonCore\.RawFileReader\.dll$'; then
    echo "ERROR: ThermoFisher.CommonCore.RawFileReader.dll not found in $PUB" >&2
    exit 1
  fi
done

# Stage initial per-RID trees
for rid in "${RIDS[@]}"; do
  SRC="$PUB_BASE/$rid"
  DEST="$OUT_BASE/$rid"
  mkdir -p "$DEST"
  rsync -a --delete "$SRC"/ "$DEST"/
  if [[ "$rid" != win-* ]]; then
    chmod +x "$DEST/MSRaw.Thermo.Server" || true
    find "$DEST" -type f \( -name '*.so' -o -name '*.dylib' \) -exec chmod +x {} \; || true
  fi
done

# Identify files identical across all RIDs to hoist into 'common/'
COMMON_DIR="$OUT_BASE/common"
mkdir -p "$COMMON_DIR"

# Build a candidate list from the first RID’s top-level files.
REF="${RIDS[0]}"
REF_DIR="$OUT_BASE/$REF"

# Ignore obvious RID- or runtime-specific files.
ignore_name() {
  local b="$1"
  case "$b" in
    MSRaw.Thermo.Server|MSRaw.Thermo.Server.exe|createdump) return 0 ;;
    *.deps.json|*.runtimeconfig.json) return 0 ;;
    # native or native-shim names
    *.dylib|*.so|*.dll.a|*.exe|lib*|*.bin) return 0 ;;
  esac
  return 1
}

declare -a CANDIDATES=()
while IFS= read -r f; do
  b="$(basename "$f")"
  # top-level files only
  if [[ -f "$REF_DIR/$b" ]] && [[ "$f" == "$REF_DIR/$b" ]]; then
    if ! ignore_name "$b"; then
      CANDIDATES+=("$b")
    fi
  fi
done < <(find "$REF_DIR" -maxdepth 1 -type f | sort)

# Function to sha256 a file
hash_of() { shasum -a 256 "$1" | awk '{print $1}'; }

# Compute intersection of identical files across all RIDs
declare -a COMMON_FILES=()
for b in "${CANDIDATES[@]}"; do
  # Must exist in all RIDs
  present_in_all=true
  for rid in "${RIDS[@]}"; do
    if [[ ! -f "$OUT_BASE/$rid/$b" ]]; then
      present_in_all=false; break
    fi
  done
  [[ "$present_in_all" == false ]] && continue
  # Must be byte-identical across RIDs
  REF_HASH="$(hash_of "$OUT_BASE/${RIDS[0]}/$b")"
  identical=true
  for rid in "${RIDS[@]:1}"; do
    H="$(hash_of "$OUT_BASE/$rid/$b")"
    if [[ "$H" != "$REF_HASH" ]]; then identical=false; break; fi
  done
  [[ "$identical" == false ]] && continue
  COMMON_FILES+=("$b")
done

echo "Found ${#COMMON_FILES[@]} common files to hoist."
# Move common files to common/ and remove from RID trees
for b in "${COMMON_FILES[@]}"; do
  cp -f "$OUT_BASE/${RIDS[0]}/$b" "$COMMON_DIR/$b"
  for rid in "${RIDS[@]}"; do
    rm -f "$OUT_BASE/$rid/$b"
  done
done

echo "Staging complete."
echo "  Common: $COMMON_DIR"
for rid in "${RIDS[@]}"; do echo "  RID:    $OUT_BASE/$rid"; done
