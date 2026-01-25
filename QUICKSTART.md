# Build Quickstart

This guide gets you from “nothing installed” to a successful build on **macOS** or **Windows (WSL 2)**.  
If you are new to programming, start with the **Java-only** build first. It confirms your Java + Maven setup before you add native toolchains.

## What you will build

- A Maven-built Java project.
- Optionally, native components built by scripts during Maven `generate-resources`.

## Choose your build path

### Path A: Java-only build (recommended first build)
Use this if you want the simplest “does it compile” check.

1) Install:
- **JDK 17+ (Temurin recommended):** https://adoptium.net/temurin/releases/?version=17
- **Maven 3.9+:** https://maven.apache.org/download.cgi  
  - Install guide: https://maven.apache.org/install.html

2) Verify:
- `java -version` (must show 17+)
- `mvn -v` (must show Maven 3.9+ and Java 17+)

3) Build (from the repo root):
- `mvn -DskipTests -Dskip.build.natives=true package`

If this works, your Java toolchain is good. Move to Path B if you need native components.

---

### Path B: Full build (Java + native components)
This is the normal packaging build, it runs native build scripts by default.

1) Install all required tools:
- JDK 17+  
  - https://adoptium.net/temurin/releases/?version=17
- Maven 3.9+  
  - https://maven.apache.org/download.cgi
- .NET SDK 8.0.x  
  - https://dotnet.microsoft.com/download/dotnet/8.0
- Rust toolchain (rustup)  
  - https://rustup.rs
- Zig 0.12+  
  - https://ziglang.org/download/
- cargo-zigbuild  
  - https://github.com/rust-cross/cargo-zigbuild

2) Confirm network access:
- Maven must reach Maven Central.
- `dotnet publish` must reach nuget.org (and any configured local NuGet feeds).

3) Verify everything is visible on PATH:
- `java -version`
- `mvn -v`
- `dotnet --info` (must show SDK 8.0.x)
- `rustc -V` and `cargo -V`
- `zig version`
- `cargo zigbuild --version` (or `cargo-zigbuild --version`, depending on install)

4) Build (from the repo root):
- `mvn -DskipTests package`

If Maven fails during native build, see Troubleshooting below.

---

## Recommended platform setup

### macOS (Homebrew)

Best install experience: Homebrew.

Copy/paste install block:

```bash
# 1) Install Xcode Command Line Tools (macOS SDK and compilers)
xcode-select --install || true

# 2) Install Homebrew (if you do not already have it)
# /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 3) Install build dependencies
brew update
brew install openjdk@17 maven dotnet@8 rustup zig

# 4) Install Rust toolchain
rustup-init -y
source "$HOME/.cargo/env"

# 5) Install cargo-zigbuild
cargo install cargo-zigbuild

# 6) Quick sanity checks
java -version
mvn -v
dotnet --info
rustc -V
cargo -V
zig version
cargo zigbuild --help
```

Optional links (if you prefer links over `xcode-select`):
- Xcode Command Line Tools  
  - https://developer.apple.com/download/all/  
  - https://developer.apple.com/xcode/resources/

Rust cross targets that may be required by scripts:
- `rustup target add x86_64-unknown-linux-gnu x86_64-pc-windows-gnu`

---

### Windows (WSL 2, Ubuntu)

Recommended: WSL 2 (Ubuntu).

Why: the build scripts are Bash-based, WSL avoids “missing Unix tools” issues.

Copy/paste install block (run **inside Ubuntu in WSL**):

```bash
# 1) Base tools
sudo apt-get update
sudo apt-get install -y   ca-certificates curl wget xz-utils tar unzip   build-essential rsync libdigest-sha-perl   openjdk-17-jdk maven

# 2) .NET SDK 8.0 (Microsoft package feed)
wget -q https://packages.microsoft.com/config/ubuntu/22.04/packages-microsoft-prod.deb
sudo dpkg -i packages-microsoft-prod.deb
rm -f packages-microsoft-prod.deb
sudo apt-get update
sudo apt-get install -y dotnet-sdk-8.0

# 3) Rust toolchain (rustup)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
source "$HOME/.cargo/env"

# 4) Zig 0.12.0 (pinned install; update the version here if you need newer)
ZIG_VERSION="0.12.0"
curl -LO "https://ziglang.org/download/${ZIG_VERSION}/zig-linux-x86_64-${ZIG_VERSION}.tar.xz"
tar -xf "zig-linux-x86_64-${ZIG_VERSION}.tar.xz"
sudo mkdir -p /opt/zig
sudo rm -rf /opt/zig/zig-linux-x86_64-"${ZIG_VERSION}"
sudo mv "zig-linux-x86_64-${ZIG_VERSION}" /opt/zig/
sudo ln -sf /opt/zig/zig-linux-x86_64-"${ZIG_VERSION}"/zig /usr/local/bin/zig
rm -f "zig-linux-x86_64-${ZIG_VERSION}.tar.xz"

# 5) cargo-zigbuild
cargo install cargo-zigbuild

# 6) Quick sanity checks
java -version
mvn -v
dotnet --info
rustc -V
cargo -V
zig version
cargo zigbuild --help
```

Also note:
- Run Maven inside WSL.
- Use Linux paths like `/home/<user>/...`.

---

## Where the native build actually happens

- Native build scripts:
  - `scripts/build-all-net.sh`
  - `scripts/build-all-rust.sh`
- These are invoked by the `core` module during Maven `generate-resources`.

If you need to bypass native builds temporarily:
- `mvn -DskipTests -Dskip.build.natives=true package`

---

## Troubleshooting common failures

### “command not found” for a tool
Cause: the tool is not installed, or not on PATH in the shell you are using.

Fix:
- Re-open your terminal after installs.
- Re-run the “Verify” commands above.

### "cargo zigbuild" not found
Fix:
- `cargo install cargo-zigbuild`
- Confirm Zig is installed and `zig version` works.

### Zig installed but not detected
Fix:
- Ensure `zig` is on PATH.
- Confirm `zig version` prints 0.12+.

### "dotnet publish" fails
Fix:
- Confirm `.NET SDK 8.0.x` is installed: `dotnet --info`.
- Confirm you can reach nuget.org from your network.
- If a corporate proxy exists, configure NuGet and Maven proxy settings.

### WSL missing tools: rsync, shasum
Fix:
- `sudo apt-get update`
- `sudo apt-get install -y rsync libdigest-sha-perl build-essential`

### WSL: "scripts/build-all-rust.sh" fails because it tries to build a macOS `.dylib`
You cannot reliably build macOS binaries on WSL without a macOS SDK/toolchain. If you are not on macOS, comment out the macOS dylib lines in `scripts/build-all-rust.sh`.

Edit the script and change the macOS dylib block to this (exactly these three lines, commented):

```bash
# echo ">> Building macOS (aarch64) dylib"
# cargo build --manifest-path "$CRATE_DIR/Cargo.toml" --release
# cp "$CRATE_DIR/target/release/libtimsrust_jni.dylib" "$OUT_DIR/osx-aarch64/"
```

After that, the script should still build:
- Linux `libtimsrust_jni.so` via `cargo zigbuild`
- Windows `timsrust_jni.dll` via `cargo zigbuild`
