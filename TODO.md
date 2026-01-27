## CORE
- option to merge IMS (one peak per m/z), smarter method to choose IMS center (mode? median intensity?)
- better spectrum naming for Thermo, maybe for Bruker as well that match proteowizard
- readers for mzML and DIA, check for missing data. Remember status lights!
- migrate todo into a real todo/changelog split to make it easier to track higher-level changes

## GUI
- searchbar for files in the table
- a way to bring up the loading panels for demos
- bug where visualization only really works for DIA data and either doesn't see DDA data for Bruker or takes forever to build for Thermo. PRM data for Thermo has problems with the structure charts

# Codebase Review Report

  ## Executive Summary

  - Highest-risk correctness issue: FragmentScan.rebuild(int, float, …) ignores the provided RT, so demultiplexed scans keep the original scan time
    and violate the demux algorithm’s intended time alignment.
  - Thermo DIA precursor m/z is a known approximation (center of isolation window) with a FIXME, which can bias downstream matching when offsets
    exist.
  - Demux pipeline can drop or error on partial cycles because CycleAssembler emits partial cycles while StaggeredDemultiplexer demands same-sized
    cycles.
  - Concurrency risk in logging: global recorder list is unsynchronized; log writes occur across multiple worker threads.
  - Strengths: strong demux test coverage, clear separation of vendor I/O from core model, deterministic output ordering and serialization in writers.
  - Strengths: well-tested model primitives and base utilities (Range, MassTolerance, Peak types).
  - Performance caution: mzML writer encodes full arrays into base64 per spectrum; can spike memory for large spectra.
  - Native boundaries are isolated (tims JNI and Thermo gRPC), easing containment of vendor-specific instability.

  ## System Map

  - Package dependency overview (simplified):

  Main
    -> io (RawFileConverters, OutputType, VendorFileFinder)
       -> io.thermo (ThermoRawFile, ThermoServerPool)
       -> io.tims (BrukerTIMSFile, TimsReader, MzCalibration*)
       -> io.encyclopedia / MGF / mzML writers
    -> algorithms (CycleAssembler, StaggeredDemultiplexer)
       -> algorithms.demux (NNLSSolver, interpolators, design matrix)
    -> model (AcquiredSpectrum, PrecursorScan, FragmentScan, Range, Peak*)
    -> logging / threading

  - Major workflows
      - CLI conversion: Main.CliArguments.call → VendorFileFinder → RawFileConverters.writeStandard → output writer.
      - Thermo demux: RawFileConverters.writeDemux → CycleAssembler → StaggeredDemultiplexer → output writer.
      - Bruker TIMS: BrukerTIMSFile → TimsReader/MzCalibration* → TIMSPeakPicker → output writer.
  - Key data models and where they live
      - AcquiredSpectrum, PrecursorScan, FragmentScan in org.searlelab.msrawjava.model
      - Windowing: Range, WindowData
      - Peak representations: Peak, PeakWithIMS, PeakInTime

  ## Global Issue Ranking

  1. [Critical][High][High] Demux RT alignment is silently broken
      - Where: org.searlelab.msrawjava.model.FragmentScan#rebuild(int,float,ArrayList)
      - Why it matters:
          - Demuxed scans are anchored to the wrong retention time, corrupting time alignment.
          - Downstream chromatographic algorithms assume RT accuracy; this introduces systematic bias.
      - Evidence:
          - FragmentScan.rebuild(int,float,ArrayList) passes scanStartTime instead of rtInsec.
          - StaggeredDemultiplexer#createSubWindowScan passes targetRT, which gets ignored.
          - Test explicitly documents the bug: core/src/test/java/org/searlelab/msrawjava/model/FragmentScanTest.java (commented “bug”).
      - Suggested fix:
          - Use the rtInsec argument when constructing the new FragmentScan.
          - Add a dedicated test that asserts the RT update after demux.
      - Suggested tests:
          - Update FragmentScanTest#rebuildWithRt to assert RT equals input RT.
          - Add demux integration test that checks monotonic RT in demux outputs.
  2. [Major][Med][High] Thermo DIA precursor m/z is a heuristic (center of isolation window)
      - Where: org.searlelab.msrawjava.io.thermo.ThermoRawFile#getStripes(...)
      - Why it matters:
          - DIA isolation windows are not always symmetric; using midpoint can misreport precursor m/z.
          - Downstream peptide matching can be biased for offset windows.
      - Evidence:
          - double precursorMz=(s.getIsoLower()+s.getIsoUpper())/2.0; // FIXME
      - Suggested fix:
          - Use the vendor-reported precursor m/z if available in gRPC Spectrum.
          - If only bounds are available, preserve bounds and mark precursor m/z as “center” explicitly.
      - Suggested tests:
          - Stub test with asymmetric isolation bounds to ensure chosen precursor m/z matches expected behavior.
  3. [Major][Med][Med] Partial cycles can destabilize demux pipeline
      - Where: org.searlelab.msrawjava.algorithms.CycleAssembler#finalizeCurrentIfNonEmpty,
        org.searlelab.msrawjava.algorithms.StaggeredDemultiplexer#demultiplex
      - Why it matters:
          - CycleAssembler accepts partial cycles (≥ half windows).
          - StaggeredDemultiplexer requires all 5 cycles to be same size and throws otherwise.
          - This can lead to silent drops (caught in RawFileConverters#getDemuxResult) or exceptions.
      - Evidence:
          - CycleAssembler accepts incomplete cycles (cycle.size() >= windowOrder.size()/2).
          - StaggeredDemultiplexer#validateCycles enforces equal sizes across cycles.
      - Suggested fix:
          - Either require full cycles in CycleAssembler, or add explicit filtering before demux.
          - Add a warning when partial cycles are dropped.
      - Suggested tests:
          - Demux test with missing windows to assert graceful skip instead of error.
  4. [Major][Med][Med] Logger recorder list is not thread-safe
      - Where: org.searlelab.msrawjava.logging.Logger
      - Why it matters:
          - Multiple worker threads call Logger during conversion/demux.
          - Unsynchronized ArrayList iteration can throw ConcurrentModificationException or lose logs.
      - Evidence:
          - Logger.recorders is a static ArrayList with no synchronization; log methods iterate directly.
      - Suggested fix:
          - Use a thread-safe collection (e.g., CopyOnWriteArrayList) or synchronize access.
      - Suggested tests:
          - Concurrency test that adds a recorder while worker threads log.
  5. [Major][Low][Med] QuickMedian pivot selection can choose out-of-range index
      - Where: org.searlelab.msrawjava.algorithms.QuickMedian#select(float[]/double[])
      - Why it matters:
          - randomInt can be negative; pivotIndex can drop below left and is clamped to 0, which can be outside the active partition.
          - This can produce incorrect quantiles or instability on certain inputs.
      - Evidence:
          - randomInt uses seed % 2147483647, which can be negative.
          - pivotIndex is clamped only to 0, not left.
      - Suggested fix:
          - Clamp pivot index to [left, right] and ensure RNG produces [0,1].
      - Suggested tests:
          - Add QuickMedian test with large arrays to detect regression against a sorted baseline.
  6. [Major][Low][Med] mzML writer builds full base64 arrays in memory

  - Where: org.searlelab.msrawjava.io.MZMLOutputFile#writeBinaryDataArrayList
  - Why it matters:
      - For large spectra, converting to byte arrays and base64 strings can spike memory.
      - This creates a performance/GC risk for large datasets.
  - Evidence:
      - toBytes64, toBytes32, and encode64 generate full arrays and strings per spectrum.
  - Suggested fix:
      - Use streaming base64 encoding or chunked buffers.
  - Suggested tests:
      - Add a large-spectrum performance test (or memory budget test) for mzML output.

  7. [Minor][Low][Med] IMS mapping may be slightly biased at the upper bound

  - Where: org.searlelab.msrawjava.io.tims.BrukerTIMSFile#getIMSFromScanNumber
  - Why it matters:
      - Uses (scanNumber-1)/scanMax rather than (scanNumber-1)/(scanMax-1), which can skew the last scan’s IMS.
      - Small but systematic scientific bias.
  - Evidence:
      - getIMSFromScanNumber formula uses scanMax directly.
  - Suggested fix:
      - Confirm scan indexing in TDF and adjust denominator if necessary.
  - Suggested tests:
      - Add a boundary test verifying IMS at first/last scan.

  ## Package Reviews

  ### Package: org.searlelab.msrawjava.algorithms

  Purpose
  Core algorithmic utilities for cycle assembly, numeric helpers, median selection, and peak interpolation. These are used by demux and tims peak
  picking for time-aligned signal handling.

  Key classes
  CycleAssembler, LogQuadraticPeakIntensityInterpolator, MatrixMath, QuickMedian, RangeCounter, StaggeredDemultiplexer

  Grades

  - Code correctness: B
  - Scientific correctness: B
  - Clarity: B
  - Brittleness/Reliability: C
  - Test adequacy: B

  Findings

  - [Major][Med][Med] QuickMedian#select can pivot outside the active partition when RNG yields negative values; may yield wrong quantiles.
      - Evidence: QuickMedian#randomInt, QuickMedian#select.
  - [Minor][Low][High] LogQuadraticPeakIntensityInterpolator indexes knots[-1] if empty input, before length check.
      - Evidence: LogQuadraticPeakIntensityInterpolator constructor uses knots[knots.length-1] before sortedPeaks.length<1 check.
  - [Major][Med][Med] CycleAssembler accepts partial cycles, which can violate demux’s equal-size expectations.
      - Evidence: CycleAssembler#finalizeCurrentIfNonEmpty and StaggeredDemultiplexer#validateCycles.

  Scientific Validations

  - Log-space interpolation uses log(intensity + e), enforcing non-negativity; acceptable but assumes intensities are non-negative.
  - Cycle assembly assumes a fixed window order; if acquisition changes order, demux alignment can drift.

  Recommended Refactors

  - Clamp QuickMedian pivot to [left, right] and normalize RNG output.
  - Validate sortedPeaks.length>0 before any knot indexing.
  - Add an option in CycleAssembler to require full cycles for demux use.

  High-Value Tests to Add

  - QuickMedian random-stability test vs sorted baseline.
  - Interpolator edge test with empty array and single-knot path.
  - CycleAssembler test where missing windows produce predictable demux behavior.

  ### Package: org.searlelab.msrawjava.algorithms.demux

  Purpose
  Implements staggered DIA demultiplexing: design matrix construction, RT interpolation, and NNLS solving.

  Key classes
  DemuxConfig, DemuxDesignMatrix, DemuxWindow, NNLSSolver, NNLSCache, CubicHermiteInterpolator, LogQuadraticInterpolator, RetentionTimeInterpolator

  Grades

  - Code correctness: B
  - Scientific correctness: B
  - Clarity: A-
  - Brittleness/Reliability: B
  - Test adequacy: A-

  Findings

  - [Minor][Low][Med] DemuxDesignMatrix#getRowIndex searches sub-window centers instead of acquired window centers; may be incorrect if used.
      - Evidence: DemuxDesignMatrix#getRowIndex uses subWindowCenters.
  - [Minor][Low][Med] NNLSCache is unused in NNLSSolver, so cache benefits aren’t realized.
      - Evidence: NNLSSolver#solve never calls NNLSCache.

  Scientific Validations

  - RT interpolation clamps outside bounds; good for chromatographic edges.
  - Log-quadratic interpolation uses log(intensity + e); avoids log(0) but may bias low-intensity areas.

  Recommended Refactors

  - Clarify getRowIndex semantics or remove if unused.
  - Integrate NNLSCache for repeated local solves.
  - Add explicit validation for sorted RT arrays where required.

  High-Value Tests to Add

  - Demux RT interpolation with uneven time spacing.
  - Design-matrix row-index behavior test for unusual window geometry.

  ### Package: org.searlelab.msrawjava.io

  Purpose
  Orchestrates conversion flows, output selection, and vendor discovery.

  Key classes
  RawFileConverters, OutputType, OutputSpectrumFile, MGFOutputFile, MZMLOutputFile, VendorFileFinder, VendorFiles, StripeFileInterface,
  ConversionParameters

  Grades

  - Code correctness: B
  - Scientific correctness: B
  - Clarity: B
  - Brittleness/Reliability: B-
  - Test adequacy: B

  Findings

  - [Major][Med][Med] Demux pipeline can enqueue partial cycles that later cause errors or dropped output.
      - Evidence: RawFileConverters#writeDemux, CycleAssembler#finalizeCurrentIfNonEmpty.

  Scientific Validations

  - mzML writer uses correct CV terms for m/z and intensity arrays; injection time units are inconsistent (see global issues).
  - MGF writer uses RTINSECONDS with scan start time (seconds).

  Recommended Refactors

  - Normalize filename handling across writers and vendors.
  - Add a demux pipeline guard to reject partial cycles or resync.
  - Add explicit null handling for source metadata in mzML.

  High-Value Tests to Add

  - Output path resolution test for Thermo demux.
  - mzML writer test for null source metadata.
  - Demux pipeline test with missing windows.

  ### Package: org.searlelab.msrawjava.io.thermo

  Purpose
  gRPC client over the local Thermo .NET server; normalizes RAW into model objects.

  Key classes
  ThermoRawFile, ThermoServerPool, GrpcServerLauncher

  Grades

  - Code correctness: B
  - Scientific correctness: C+
  - Clarity: B
  - Brittleness/Reliability: B
  - Test adequacy: B

  Findings

  - [Major][Med][High] Precursor m/z is approximated as window midpoint; known FIXME.
      - Evidence: ThermoRawFile#getStripes.
  - [Minor][Low][Med] Channel and server lifecycle are robust, but log suppression uses console state and can hide errors.
      - Evidence: GrpcServerLauncher output redirection.

  Scientific Validations

  - RT is returned in seconds from gRPC and used consistently in model objects.
  - Isolation bounds preserved from vendor; precursor m/z is approximated.

  Recommended Refactors

  - Prefer vendor-provided precursor m/z if available.
  - Align getOriginalFileName() with interface semantics.
  - Add explicit diagnostic logging on server startup failure.

  High-Value Tests to Add

  - Asymmetric isolation test for precursor m/z.
  - Output file name test for demux path resolution.

  ### Package: org.searlelab.msrawjava.io.tims

  Purpose
  Bruker timsTOF reader and calibration pipeline; bridges JNI and calibrated model construction.

  Key classes
  BrukerTIMSFile, TimsReader, TIMSPeakPicker, MzCalibrationPoly/Linear, TIMSMassTolerance, TimsNative

  Grades

  - Code correctness: B
  - Scientific correctness: C
  - Clarity: B
  - Brittleness/Reliability: C+
  - Test adequacy: B

  Findings

  - [Minor][Low][Med] IMS mapping uses scanMax denominator without -1, potentially skewing bounds.
      - Evidence: BrukerTIMSFile#getIMSFromScanNumber.
  - [Minor][Low][Med] NativeLibraryLoader hardcodes Apple Silicon RID; Intel macs may fail.
      - Evidence: NativeLibraryLoader#load.

  Scientific Validations

  - Calibration uses polynomial ToF conversion consistent with README; includes C1/C2 corrections and temperature adjustment.
  - TIMS peak picking uses IMS tolerance and Gaussian smoothing; deterministic.

  Recommended Refactors

  - Standardize injection time units at the model boundary (seconds).
  - Validate IMS mapping against vendor documentation for scan indexing.
  - Expand NativeLibraryLoader to support mac x86_64 if needed.

  High-Value Tests to Add

  - Injection time unit consistency test (MS1 vs MS2).
  - IMS boundary test for scan 1 and scan max.
  - Calibration round-trip test for mzToTof/tofToMz.

  ### Package: org.searlelab.msrawjava.io.encyclopedia

  Purpose
  SQLite-based writer/reader for EncyclopeDIA .DIA files with binary serialization and compression.

  Key classes
  EncyclopeDIAFile, SQLFile, ByteConverter, CompressionUtils

  Grades

  - Code correctness: B
  - Scientific correctness: B
  - Clarity: B
  - Brittleness/Reliability: B
  - Test adequacy: B

  Findings

  - [Minor][Low][Med] SQL schema checks use string matching which can be brittle across formatting.
      - Evidence: SQLFile#doesColumnExist.
  - [Minor][Low][Low] Compression utilities do not bound output for corrupted data; can inflate large buffers.
      - Evidence: CompressionUtils#decompress.

  Scientific Validations

  - Model fields map directly to database schema; intensity/mz arrays stored as binary blobs with explicit byte order.

  Recommended Refactors

  - Use SQLite PRAGMA for column existence checks.
  - Add upper bounds or sanity checks in decompression for corrupted payloads.

  High-Value Tests to Add

  - Corrupt-compression handling test.
  - Round-trip test for byte encoding of mz/intensity arrays.

  ### Package: org.searlelab.msrawjava.io.utils

  Purpose
  File/resource helpers for streaming and extraction.

  Key classes
  StreamCopy, ResourceTreeExtractor, Pair, Triplet

  Grades

  - Code correctness: A-
  - Scientific correctness: A
  - Clarity: A-
  - Brittleness/Reliability: B
  - Test adequacy: B

  Findings

  - [Minor][Low][Low] Resource extraction path handling relies on jar: URLs; unusual classloaders may fail.
      - Evidence: ResourceTreeExtractor#extractDirectory.

  Scientific Validations

  - Not directly scientific.

  Recommended Refactors

  - Add fallback for non-standard classloaders if needed.

  High-Value Tests to Add

  - Resource extraction tests for both file: and jar: contexts.

  ### Package: org.searlelab.msrawjava.model

  Purpose
  Canonical in-memory data model for MS1/MS2 spectra and windows.

  Key classes
  AcquiredSpectrum, PrecursorScan, FragmentScan, Range, MassTolerance, PPMMassTolerance, Peak*, WindowData

  Grades

  - Code correctness: B
  - Scientific correctness: B
  - Clarity: A-
  - Brittleness/Reliability: B
  - Test adequacy: A

  Findings

  - [Critical][High][High] FragmentScan#rebuild ignores RT input (documented by test).
      - Evidence: FragmentScan#rebuild(int,float,ArrayList); FragmentScanTest#rebuildWithRt.
  - [Minor][Low][Med] Range.contains(Range) is overlap, not containment, which can be misleading.
      - Evidence: Range#contains(Range).

  Scientific Validations

  - AcquiredSpectrum#getIonInjectionTime is seconds; affects writer unit selection.
  - MassTolerance assumes sorted arrays; callers must enforce sort.

  Recommended Refactors

  - Fix RT handling in rebuild.
  - Clarify Range.contains(Range) naming or add overlaps.

  High-Value Tests to Add

  - RT correctness test for rebuild.
  - Range overlap vs containment test to clarify semantics.

  ### Package: org.searlelab.msrawjava.logging

  Purpose
  Lightweight logging and CLI progress UX.

  Key classes
  Logger, ConsoleStatus, LoggingProgressIndicator, FileLogRecorder

  Grades

  - Code correctness: B
  - Scientific correctness: A
  - Clarity: B
  - Brittleness/Reliability: C
  - Test adequacy: B

  Findings

  - [Major][Med][Med] Logger recorder list is not thread-safe for concurrent logging.
      - Evidence: Logger.recorders static ArrayList with unsynchronized access.

  Scientific Validations

  - Not directly scientific.

  Recommended Refactors

  - Use thread-safe collection for recorders.
  - Add defensive exception handling around recorder loops.

  High-Value Tests to Add

  - Multi-threaded logging stress test.

  ### Package: org.searlelab.msrawjava.threading

  Purpose
  Managed compute pool for parallel processing.

  Key classes
  ProcessingThreadPool

  Grades

  - Code correctness: A-
  - Scientific correctness: A
  - Clarity: B
  - Brittleness/Reliability: B
  - Test adequacy: B

  Findings

  - [Minor][Low][Low] Thread pool rejection handler blocks; acceptable but can backpressure UI unexpectedly.
      - Evidence: ProcessingThreadPool.BlockOnRejectPolicy.

  Scientific Validations

  - Not directly scientific.

  Recommended Refactors

  - Document blocking behavior for callers.

  High-Value Tests to Add

  - Test pool backpressure under heavy task load.

  ### Package: org.searlelab.msrawjava (root)

  Purpose
  CLI entry point and versioning.

  Key classes
  Main, Version

  Grades

  - Code correctness: B
  - Scientific correctness: A
  - Clarity: B
  - Brittleness/Reliability: B
  - Test adequacy: B

  Findings

  - [Minor][Low][Med] CLI demux uses a full path for Thermo output (see global issue #5).
      - Evidence: Main.convertKnownFiles + RawFileConverters.writeDemux.

  Scientific Validations

  - CLI option defaults align with README.

  Recommended Refactors

  - Align filename semantics across readers.

  High-Value Tests to Add

  - CLI output path test for demux mode.

  ### Package: org.searlelab.msrawjava.exceptions

  Purpose
  Domain-specific exceptions for TDF parsing.

  Key classes
  TdfFormatException

  Grades

  - Code correctness: A
  - Scientific correctness: A
  - Clarity: A
  - Brittleness/Reliability: A
  - Test adequacy: C

  Findings

  - [Minor][Low][Low] No direct tests for exception usage; acceptable.

  Scientific Validations

  - Not directly scientific.

  Recommended Refactors

  - None.

  High-Value Tests to Add

  - None critical.

  ## Cross-Cutting Concerns

  - Error handling: Several paths swallow exceptions (e.g., RawFileConverters#getDemuxResult, CompressionUtils#decompress), which can hide data loss.
  - Logging: Global logger is not thread-safe and can lose events during parallel conversions.
  - Configuration: Units are not normalized at a single boundary; injection time is a recurring unit mismatch risk.
  - Performance: mzML writing constructs full base64 payloads per spectrum, which can spike memory; demux uses per-transition NNLS solve without
    caching.
  - Concurrency: Demux compute + writer pipeline is sound, but partial cycles can trigger exceptions and drop data.
  - Reproducibility: Output ordering is deterministic, but RT misassignment undermines time-based reproducibility.
