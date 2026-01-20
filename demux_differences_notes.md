# Demultiplexing Differences (Java vs pwiz OverlapDemultiplexer)

Assumption: pwiz/OverlapDemultiplexer is the gold standard. This list is ordered by likely negative impact on Java demux accuracy, with emphasis on the NNLS zero‑intensity dropout problem.

## 1) Spectrum selection for the local solve (m/z‑space vs RT‑nearest)
**Impact:** Very high. Wrong spectra in the local block change both X (mask) and y (signal), which can yield under‑determined or mis‑conditioned systems and zeros in the NNLS solution.

- **pwiz behavior:** For the spectrum to demux, select `overlapRegionsInApprox_` spectra based on distance in *demux window index space*, not RT proximity. It computes a “center of deconv indices” and uses `demuxWindowDistances` to choose the nearest in m/z space (`OverlapDemultiplexer::BuildDeconvBlock`).
- **Java behavior:** Selects spectra that cover the sub‑window, then chooses the k nearest by **RT distance** (`StaggeredDemultiplexer.findCoveringSpectra`, `StaggeredDemultiplexer.selectKNearest`).

**Evidence:**
- `core/src/main/java/org/searlelab/msrawjava/algorithms/StaggeredDemultiplexer.java` (methods `findCoveringSpectra`, `selectKNearest`).
- `/Users/searle.brian/Documents/temp/skyline/pwiz/pwiz/analysis/demux/OverlapDemultiplexer.cpp` (section building `demuxWindowDistances` and `bestMaskAverages`).

## 2) Interpolation + weighting behavior
**Impact:** High. Java’s interpolation + weighting can shrink intensities toward zero even when pwiz preserves them.

- **pwiz behavior:** When `interpolateRetentionTime=true`, each transition is interpolated to the deconv scan’s RT using cubic Hermite spline. The interpolated intensities are used directly as the signal row (no additional per‑scan weighting). (`OverlapDemultiplexer::InterpolateMuxRegion`).
- **Java behavior:** Builds an intensity vector across selected spectra, then interpolates (via `RetentionTimeInterpolator`) and additionally applies an RT‑distance weight to each spectrum’s intensity in the vector (`StaggeredDemultiplexer.buildIntensityVector`). This weighting happens *even when interpolation is used*, which can suppress already sparse signals.

**Evidence:**
- `core/src/main/java/org/searlelab/msrawjava/algorithms/StaggeredDemultiplexer.java` (`buildIntensityVector`).
- `/Users/searle.brian/Documents/temp/skyline/pwiz/pwiz/analysis/demux/OverlapDemultiplexer.cpp` (`InterpolateMuxRegion` block).
- Supplementary Note (PDF) §1.1: spline interpolation on nearest scans before NNLS.

## 3) Design matrix / mask construction and column alignment
**Impact:** High–medium. Column misalignment or missing edge handling can push true signal into wrong columns or zeros.

- **pwiz behavior:** Builds masks via `PrecursorMaskCodec::GetMask`, using demux window indices from precursor masks. It trims to a specific sub‑window range (`lowerMZBound` + `numDemuxSpectra`), so columns correspond exactly to the local demux window block.
- **Java behavior:** Constructs local matrices by checking whether each sub‑window is contained within a spectrum’s isolation bounds and uses a *centered k‑column window* around the target sub‑window (`buildLocalMatrix`, `getLocalSubWindowIndex`). This does not mirror pwiz’s `lowerMZBound` selection logic and may shift column indices relative to the mask used in pwiz.

**Evidence:**
- `core/src/main/java/org/searlelab/msrawjava/algorithms/StaggeredDemultiplexer.java` (`buildLocalMatrix`, `getLocalSubWindowIndex`).
- `/Users/searle.brian/Documents/temp/skyline/pwiz/pwiz/analysis/demux/PrecursorMaskCodec.cpp` (`GetMask`, `IdentifyOverlap`).
- `/Users/searle.brian/Documents/temp/skyline/pwiz/pwiz/analysis/demux/OverlapDemultiplexer.cpp` (mask fill with `lowerMZBound`).

## 4) Peak extraction / binning strategy for transitions
**Impact:** Medium. Missing or mis‑binned peaks can cause zeros in y for a transition even if signal exists.

- **pwiz behavior:** Uses a fixed transition list (mzs from the deconv spectrum) and bins every spectrum into *non‑overlapping m/z ranges* via `SpectrumPeakExtractor`. This ensures each transition is measured consistently across all rows and avoids tolerance overlap duplication.
- **Java behavior:** Uses tolerance‑based matching against raw peak lists per spectrum (`buildIntensityVector` + `MassTolerance.getIndices`). No explicit bin segmentation or overlap correction like pwiz’s `SpectrumPeakExtractor` does.

**Evidence:**
- `core/src/main/java/org/searlelab/msrawjava/algorithms/StaggeredDemultiplexer.java` (`collectMzValuesFromAnchor`, `buildIntensityVector`).
- `/Users/searle.brian/Documents/temp/skyline/pwiz/pwiz/analysis/demux/SpectrumPeakExtractor.cpp` (range binning and overlap correction).

## 5) NNLS implementation and numerical behavior
**Impact:** Medium. Different NNLS implementations can resolve under‑determined systems differently, affecting zeroing behavior.

- **pwiz behavior:** Uses a dedicated NNLS implementation (`nnls.h`) and solves each transition independently; supports caching of QR decompositions for row subsets (per Supplementary Note §1.2). (`DemuxSolver::NNLSSolver::Solve`).
- **Java behavior:** Uses EJML least‑squares in each iteration of a Lawson–Hanson implementation without cached QR subsets, potentially changing convergence and numerical stability (`core/src/main/java/org/searlelab/msrawjava/algorithms/demux/NNLSSolver.java`).

**Evidence:**
- `core/src/main/java/org/searlelab/msrawjava/algorithms/demux/NNLSSolver.java`.
- `/Users/searle.brian/Documents/temp/skyline/pwiz/pwiz/analysis/demux/DemuxSolver.cpp`.
- Supplementary Note §1.2 (QR caching, k=7). 

## 6) Cycle/block structure and indexing assumptions
**Impact:** Medium–low. If cycles are mis‑grouped, spectrum ordering differences can cascade into selection/mask mismatches.

- **pwiz behavior:** Uses spectrum indices and `GetMatrixBlockIndices` to pick a windowed block of MS2 scans centered on the spectrum to demux (scan index–centric).
- **Java behavior:** Builds cycles by sorting spectra by RT and grouping by window count (see `extractCycles` in tests) and uses 5 cycles for demux; there is no direct notion of scan index or stride as in pwiz.

**Evidence:**
- `core/src/test/java/org/searlelab/msrawjava/algorithms/demux/PwizValidationTest.java` (`extractCycles`).
- `/Users/searle.brian/Documents/temp/skyline/pwiz/pwiz/analysis/demux/OverlapDemultiplexer.cpp` (`GetMatrixBlockIndices`).

## 7) Edge sub‑window handling
**Impact:** Low–medium. Edge behavior can drop spectra or change counts but is less likely to cause a specific internal dropout.

- **pwiz behavior:** `PrecursorMaskCodec` can remove non‑overlapping edges (`removeNonOverlappingEdges`), while still using a uniform mask approach.
- **Java behavior:** Configurable include/exclude edges with `DemuxConfig.isIncludeEdgeSubWindows`, but edge inclusion does not follow pwiz’s specific edge‑removal semantics.

**Evidence:**
- `core/src/main/java/org/searlelab/msrawjava/algorithms/demux/DemuxConfig.java`.
- `/Users/searle.brian/Documents/temp/skyline/pwiz/pwiz/analysis/demux/PrecursorMaskCodec.hpp` (params `removeNonOverlappingEdges`).

## Investigation Plan (targeted to peak dropout)

Assumptions based on user input:
- Peak extraction may be implicated (mass accuracy / matching across spectra).
- Spectrum selection and design matrix are *probably* correct given scan structure but should be verified.
- Interpolation likely not the issue; Java’s approach may be more correct.
- Both sides use Lawson–Hanson NNLS (per Supplementary Note).

### Plan

1) **Peak extraction parity and mass‑accuracy checks**  
   - Add a diagnostic in `PwizValidationTest.testGTGIVSAPVPK_PeakDropout` that builds a pwiz‑style “transition list” from the anchor spectrum, then applies both (a) Java tolerance matching and (b) pwiz‑style non‑overlapping binning to the same spectra.  
   - Compare y2/y7 match counts and intensities across cycles at the target RT and neighbors.  
   - If Java matching misses peaks that pwiz binning captures, quantify delta and capture the exact m/z offsets per spectrum.  
   **Evidence targets:** `core/src/main/java/org/searlelab/msrawjava/algorithms/StaggeredDemultiplexer.java` (`collectMzValuesFromAnchor`, `buildIntensityVector`), pwiz `SpectrumPeakExtractor.cpp`.

2) **Spectrum selection equivalence tests (m/z‑space vs RT‑nearest)**  
   - In `PwizValidationTest`, compute both selection orders for the target sub‑window:  
     a) Java’s current RT‑nearest selection.  
     b) pwiz‑style selection by demux window index distance (derive window indices from the cycle’s isolation windows).  
   - Assert the selected spectrum sets are identical (or log the symmetric difference with RT and isolation bounds).  
   - If they differ, record whether y2/y7 are present in the “missing” spectra.  
   **Evidence targets:** `StaggeredDemultiplexer.selectKNearest`, `OverlapDemultiplexer::BuildDeconvBlock`.

3) **Design matrix alignment checks**  
   - Build the pwiz‑style mask row for each selected spectrum (using sub‑window membership) and compare to Java’s local matrix row for the same spectrum; verify column alignment for the target sub‑window.  
   - Add an assertion that the target sub‑window column is 1 for all spectra that cover it and 0 otherwise.  
   **Evidence targets:** `StaggeredDemultiplexer.buildLocalMatrix`, `DemuxDesignMatrix`, pwiz `PrecursorMaskCodec::GetMask`.

4) **NNLS solver equivalence sanity**  
   - For the target RT and sub‑window, dump X and y to a debug file and solve with the current Java NNLS and a direct NNLS reference (if available in tests) to verify that zeros are not a solver artifact.  
   - If the solution still zeros the target sub‑window, the issue is upstream (X/y composition, peak matching, or selection).  
   **Evidence targets:** `core/src/main/java/org/searlelab/msrawjava/algorithms/demux/NNLSSolver.java`, pwiz `DemuxSolver.cpp`.

5) **Interpolation validation (informational only)**  
   - Log interpolated intensities for y2/y7 across the selected spectra, but do not change the interpolation code.  
   - Confirm interpolation does not zero values that are present in multiple adjacent scans.  
   **Evidence targets:** `RetentionTimeInterpolator` implementations and `buildIntensityVector`.

### Expected outputs
- A reproducible table for GTGIVSAPVPK y2/y7 showing: per‑spectrum m/z offset, matched intensity (Java vs pwiz binning), and whether the spectrum is included in the local block.  
- Confirmation (or refutation) that selection/matrix setup is identical to pwiz for the target case.  
- If peak dropout persists with identical X/y, isolate NNLS‑level causes; otherwise focus on the upstream matching/selection mismatch.
