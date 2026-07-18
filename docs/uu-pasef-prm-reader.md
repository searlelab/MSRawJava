# Add timsTOF PASEF PRM MS2 support

**Canonical plan document:** `docs/uu-pasef-prm-reader.md`

## Intent

Make `BrukerTIMSFile` read PASEF PRM MS2 data (`MsMsType=10`) as one `FragmentScan` per `PrmFrameMsMsInfo` row. Preserve current DDA (8) and DIA (9) behavior.

## Current understanding

- PRM is multi-target per frame: each target occupies its own `[ScanNumBegin, ScanNumEnd]` ion-mobility slice. The supplied runs have two or three targets per PRM frame.
- `PrmFrameMsMsInfo` provides the frame, slice, and isolation window; `PrmTargets` provides target m/z, charge, and description.
- PRM/DIA/DDA schema tables may exist empty in other acquisition types, so all new behavior must be guarded by `ms2Key()==10`.
- `BrukerTIMSFile.openFile` currently leaves the key unset for `{0,10}` histograms; `BrukerTimsSpectrumReader.getStripes` therefore returns no MS2.
- Existing DIA `getSpectrum` indexing is not suitable as a PRM model: PRM must use the same frame-and-slice identity in stripe extraction and scan summaries.

## Implementation approach

1. Write this unchanged plan to `docs/uu-pasef-prm-reader.md` before implementation changes.
2. In `BrukerTIMSFile`:
   - Add an explicit `MsMsType=10` selection after DIA/DDA priority and expose `isPASEFPRM()`.
   - Populate PRM display ranges from `PrmFrameMsMsInfo`, aggregating repeated isolation windows with RT and IM bounds.
   - When `ms2Key==10`, set `dataAcquisitionType=PRM`, `staggered=false`, and `precursorMarginSize=0` directly; do not route known PRM data through the geometric PRM/DIA heuristic.
   - Add `frames.ms2.prm` and concise `PrmTargets` / `PrmFrameMsMsInfo` metadata summaries.
3. In `BrukerTimsSpectrumReader`, add PRM-only paths for both `getStripes` overloads and `getScanSummaries`:
   - Join `Frames`, `PrmFrameMsMsInfo`, and `PrmTargets`; emit one spectrum/summary per matching PRM row.
   - For target-m/z lookup, match the requested m/z against `IsolationMz ± IsolationWidth/2`; for range lookup, match `PrmTargets.MonoisotopicMz` against the requested range. Both apply the requested RT range.
   - Reuse the DIA-style per-window slicing pattern: call `readRawFrameAndCalibrate(frameId-1, scanLo, scanHi, t1)` for every PRM row, including empty slices, then construct `FragmentScan` with PRM target annotation and the row’s isolation bounds.
   - Emit empty PRM spectra rather than skipping them, so API counts deterministically equal the joined PRM-row count.
4. Define one shared PRM spectrum-index helper used by both extraction and summaries: pack `(frameId, scanNumBegin)` using a file-specific stride greater than the file’s maximum `NumScans`. Compute and validate the stride during open; reject a run whose packed index would exceed `Integer.MAX_VALUE`. This gives `getSpectrum` an exact, stable identity for repeated targets rather than allowing its fallback to return a neighboring frame.

## Validation approach

- Extend `TIMSFullDIAandDDAIT` with opt-in PRM integration coverage for both supplied PRM directories; do not copy the 1.2–1.8 GB runs into repository fixtures.
- Compare full-range stripe and summary counts to `PrmFrameMsMsInfo JOIN Frames JOIN PrmTargets WHERE MsMsType=10`, including empty spectra.
- Assert `isPASEFPRM`, PRM structure metadata, zero margin/non-staggered behavior, target m/z/charge/description propagation, per-row IM slice naming and bounds, RT and m/z filtering, sorted output, and exact summary-to-spectrum resolution.
- Run focused TIMS tests, then `mvn -pl core,gui -am -Dskip.build.natives=true -Dmaven.test.skip=true compile`.

## Risks, decisions, and reviewer focus

- The packed `int` index has an explicit run-size bound; validate it at open rather than silently allowing collisions or overflow.
- PRM ranges are retained for visualization and output metadata only. PRM must never activate DIA demultiplexing or precursor-margin inference.
- `PrmFrameMeasurementMode` is intentionally not read for spectrum construction: it has no required slice or precursor fields, and supplied values are empty.
