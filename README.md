# MSRawJava: Java-first readers for Bruker timsTOF and Thermo RAW
MSRawJava is a Java-first toolkit for efficiently reading Bruker timsTOF `.d` and Thermo `.raw` files on Windows, Linux, and MacOS X. The code provides additional tooling for reading spectra and metadata into a standard object model, and producing analysis-ready outputs (`.mzML`, `.MGF`, [EncyclopeDIA](https://bitbucket.org/searleb/encyclopedia) `.DIA`). The library exposes a uniform Java API and a compact CLI. Vendor specifics are isolated behind a JNI bridge for Bruker and a local gRPC client for Thermo, so the public surface remains consistent across platforms.

## What MSRawJava is and is not
**MSRawJava is not a [ProteoWizard](https://proteowizard.sourceforge.io/) replacement.** MSRawJava has a significantly smaller surface area than ProteoWizard, supporting only a narrow set of Vendor files and output formats. Additionally, there are very limited options for processing raw files and no options for non-vendor peak picking. In exchange, MSRawJava offers higher conversion speed and a more straightforward interface for daily work in the GUI, at the command line, and via programmatic interfaces. It also provides rich, integrated visualization of raw files, with a forensic focus to catch mistakes in data acquisition. This visualization focuses on rapid understanding of the entire raw data file (rather than spectrum by spectrum), enabling interrogation of windowing and global trends. Lastly, it offers high (>90%) test coverage in the core code and first-class support for Apple computers as well as Linux and Windows. **Use ProteoWizard for completeness and MSRawJava for "real-life" tasks.**

## Architecture and code structure
The library normalizes vendor streams into a compact, immutable model under `model`. `AcquiredSpectrum` defines the common surface for MS1 and MS2 and is implemented by `PrecursorScan` and `FragmentScan`. Both carry `spectrumName`, `spectrumIndex`, `scanStartTime`, `fraction`, isolation/scan window bounds, optional `ionInjectionTime`, and primitive `double[]` m/z with `float[]` intensity; timsTOF adds an optional `float[]` ion mobility vector where every peak gets an assigned ion mobility value. `Peak` is a lightweight value object (`mz`, `intensity`, `ims`) materialized on demand when point lists are needed, avoiding overhead during bulk transfer. Windowing is represented by `Range` (the m/z interval) and `WindowData` (average duty cycle, MS/MS counts, optional ion-mobility span). Readers construct a `Map<Range, WindowData>` of DIA stripes, stream MS1 as `PrecursorScan` and `FragmentScan` records sliced using retention time and m/z windows (for `FragmentScan`s). Arrays remain primitive to enable zero-copy JNI/gRPC exchange; conversion to `Peak` lists is threshold-aware and performed lazily only when necessary. The model’s immutability supports parallel read stages while writers serialize output for deterministic archives.

From the Java layer, timsTOF access is implemented in `src/main/java/org/searlelab/msrawjava/io/tims` to use [TimsRust](https://github.com/MannLabs/timsrust) by Sander Willems (Mann lab/Bruker). `BrukerTIMSFile` is the main entry point; it coordinates metadata reads from the TDF, frame and scan extraction, and conversion to model objects. Calibration and mobility/mass tolerances are handled by `MzCalibrationParams`, `MzCalibrationPoly`, and `TIMSPeakPicker`. Native interaction is confined to `TimsNative` with dynamic loading via `NativeLibraryLoader`, keeping all JNI mechanics out of the main code logic. `MzCalibrationPoly` uses a standard polynomial time correction calculation: `m = k2×(t-t0)² + k4×(t-t0)⁴` (typically accurate to ±10 PPM), which is significantly more accurate than the linear calibration in TimsRust (typically accurate to ±0.2-0.3 m/z). `TIMSPeakPicker` performs peak detection along the ion‑mobility axis (1/K0) after m/z calibration. The peak picker treats each calibrated m/z trace as a one‑dimensional signal over mobility, estimates a local baseline with a robust statistic, and segments contiguous regions above an adaptive threshold. Each region is smoothed with a small Gaussian kernel to suppress single‑bin spikes, the apex is chosen by the local maximum, and the integrated area across the region becomes the new centroid intensity. Neighboring m/z bins are merged within a ToF-specific tolerance to avoid duplicate peaks from fractional binning. Results are emitted as `Peak(mz, intensity, imsApex)`s and fed into `PrecursorScan`/`FragmentScan` spectra. The algorithm is deterministic, linear in the length of the mobility trace, and designed to keep garbage collection pressure low.

Thermo RAW access is implemented in `src/main/java/org/searlelab/msrawjava/io/thermo` to use [RawFileReader](https://github.com/thermofisherlsms/RawFileReader) by Jim Shofstahl (Thermo). `ThermoRawFile` is a thin client that talks to a local, self-contained .NET server process managed by `GrpcServerLauncher` and pooled by `ThermoServerPool`. The client exposes the same high-level methods as the timsTOF reader, including TIC, gradient length, DIA windows, and MS1/MS2 extraction. A small gRPC server is used because calling Thermo’s C#/.NET code directly from Java via JNI can be messy and crash-prone, while gRPC lets the C# code run safely in its own process. Because the server is launched on demand and bound to the current OS and architecture, Java code does not depend on COM or system-installed SDKs.

Example file writers show how data is organized for streaming and reproducible output. `MZMLOutputFile`, `MGFOutputFile`, and `EncyclopeDIAFile` write spectra and metadata to disk while enforcing deterministic ordering. `OutputSpectrumFile` and `OutputType` provide small abstractions over destination formats. Optional developer plots reside under `gui` (`IMSChromatogramChart`, `SpectrumChart`, and `MobilogramHeatmap`) and are not required for headless use. 

## Building the code
**Complete build instructions are in `QUICKSTART.md`.** This project targets Java 17 and builds with Maven. Packaging compiles Java and stages the native components (Rust/JNI for Bruker, self-contained .NET gRPC server for Thermo). Dependencies include: JDK 17+, Maven 3.9+, .NET SDK 8.0.x (for the Thermo server), Rust toolchain (rustup, rustc, cargo), and Zig (version≥0.12) and `cargo-zigbuild` for cross-compiling JNI. The software can be built using the command:

```
mvn -DskipTests package
```

Sometimes Maven struggles with the one-line command. To prebuild the native side explicitly, use the following commands:

```
scripts/build-all-net.sh 
scripts/build-all-rust.sh 
mvn -DskipTests package
```

Note, you need to run scripts/build-all-net.sh before scripts/build-all-rust.sh because the net.sh script cleans the target space. Building the MacOS Rust libraries on Windows may fail, so you may need to comment that section of the `build-all-rust.sh` script.

Bruker timsTOF-specific Rust binaries are embedded in the jar under `META-INF/lib/{os-arch}` and `resources/msraw/thermo/bin/{rid}` for the Thermo server. The runtime loaders (`NativeLibraryLoader`, `GrpcServerLauncher`) resolve and launch them automatically.


## CLI usage
The CLI in `Main.java` accepts files or directories and searches them for Bruker `.d` and Thermo `.raw` files, with optional EncyclopeDIA `.dia` discovery when enabled. Command line options include:

```
  -f, --format [fmt]        Output format: dia|mgf|mzml (default dia)
  -o, --output [path]       Where new files get written (default same directory as input)
  --log-file [path]         Write log output to a file (overwrites on each run)
  --min-ms1 [#]             Minimum MS1 intensity threshold for timsTOF (default 3.0)
  --min-ms2 [#]             Minimum MS2 intensity threshold for timsTOF (default 1.0)
  --demux                   Enable staggered window demultiplexing for Thermo DIA
  --demux-k [#]             Local approximation size for demux (7-9, default 7)
  --demux-interp [method]   Interpolation: cubic|logquadratic (default cubic)
  --demux-exclude-edges     Exclude edge sub-windows from demux output
  --demux-ppm [#]           Mass tolerance in ppm for demux (default 10.0)
  --discoverDIAFiles        Allow directory discovery of EncyclopeDIA .dia files
  --batch                   Disable status bar and progress updates
  --no-ansi                 Disable ANSI output, even on TTYs
  --silent                  Suppress all non-error output
```

Examples:

```
java -jar MSRawJava path/to/raws/
java -jar MSRawJava -f mgf ../../path/to/raws/
java -jar MSRawJava -f mzml /mnt/vol1/path/to/raws/ --min-ms1 10.0 --min-ms2 5.0
java -jar MSRawJava -f dia --demux --demux-ppm 10.0 /mnt/vol1/path/to/raws/
java -jar MSRawJava -f dia --log-file run.log /mnt/vol1/path/to/raws/
java -jar MSRawJava --discoverDIAFiles -f dia --demux /mnt/vol1/path/to/dia/
```
