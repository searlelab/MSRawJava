# mzML Reader Implementation Tasklist

## Core Reader
- [x] `MzmlFile.java` — Core reader implementing `StripeFileInterface`

## Registration & Discovery
- [x] `VendorFile.java` — Add `MZML` enum constant + `isMzMLFile()` convenience
- [x] `VendorFiles.java` — Add mzML list field + accessors
- [x] `VendorFileFinder.java` — Discover .mzML files (flag-gated)
- [x] `ConversionParameters.java` — Add `discoverMzMLFiles` field

## CLI Wiring
- [x] `Main.java` — Add `--discoverMzMLFiles` flag and mzML processing block

## GUI Integration
- [x] `FileDetailsDialog.java` — Add `VendorFile.MZML` case
- [x] `ConversionPane.java` — Add `VendorFile.MZML` case in conversion + output override
- [x] `DirectorySummaryPanel.java` — Add mzML rows + slow-bits extraction
- [x] `ReaderStatusPanel.java` — Add mzML status line

## Tests
- [x] `MzmlFileTest.java` — Unit tests (13/13 passing)
- [x] `MzmlRoundTripIT.java` — Round-trip integration test (DIA -> mzML -> DIA)
- [x] `VendorFileTest.java` — Updated for MZML enum constant

## Verification
- [x] Core module compiles
- [x] GUI module compiles
- [x] All 647 tests pass (0 failures, 0 errors)
