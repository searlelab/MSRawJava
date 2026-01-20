# Plan: Restructure StaggeredDemultiplexer to Spectrum-Centric Approach

## Goal
Restructure the demultiplexing loop to iterate by acquired spectrum (like pwiz) instead of by subWindow, anchoring retention times to actual spectrum RTs for more accurate intensity estimation.

## Key Changes

### Current Approach (subWindow-centric)
- Iterates over subWindows: `for (subWindow in subWindows)`
- Uses averaged targetRT from cycleM1/cycleP1
- Produces 1 demuxed spectrum per subWindow per cycle (N+M+1 outputs)

### New Approach (spectrum-centric)
- Iterates over acquired spectra: `for (spectrum in cycleCenter)`
- Uses each spectrum's actual RT as anchor
- Each spectrum covers 2 subWindows → produces 2 demuxed outputs
- Edge subWindows: parameterized option to include or exclude (default: include)
- Total with edges: 2*(N+M) demuxed spectra per cycle
- Total without edges: 2*(N+M) - 2 (exclude first and last subWindow outputs)

### Critical Constraint: Peaks from Anchor Only
- **NNLS calculation uses ONLY peaks present in the anchorSpectrum**
- Do NOT add or consider peaks from other covering spectra
- Each demuxed subWindow spectrum contains only m/z values from its anchor

## Implementation Steps

### Step 1: Add parameterized method signature
**File:** `StaggeredDemultiplexer.java`

Add overloaded demultiplex method with edge parameter:
```java
// Default method - includes edge subWindows
public ArrayList<FragmentScan> demultiplex(
    ArrayList<FragmentScan> cycleM2, ..., int currentScanNumber) {
    return demultiplex(cycleM2, ..., currentScanNumber, true);  // includeEdges=true
}

// Parameterized method
public ArrayList<FragmentScan> demultiplex(
    ArrayList<FragmentScan> cycleM2, ..., int currentScanNumber,
    boolean includeEdgeSubWindows) {
    // Main implementation
}
```

### Step 2: Add helper method `findCoveredSubWindows()`
**File:** `StaggeredDemultiplexer.java`
```java
private DemuxWindow[] findCoveredSubWindows(FragmentScan scan) {
    // Returns the 2 subWindows contained within this spectrum's isolation window
    // Uses DemuxWindow.isContainedBy() with tolerance
}
```

### Step 3: Add helper method `collectMzValuesFromAnchor()`
**File:** `StaggeredDemultiplexer.java`
```java
private TDoubleArrayList collectMzValuesFromAnchor(FragmentScan anchor) {
    // Collect ALL m/z values from the SINGLE anchor spectrum ONLY
    // Do NOT include peaks from any other spectra
    // Each demuxed output contains only peaks present in its anchor
}
```

### Step 4: Modify intensity vector building for anchor peaks only
**File:** `StaggeredDemultiplexer.java`
- For each m/z in the anchor spectrum:
  - Find matching peaks in surrounding spectra (for NNLS context)
  - Interpolate those intensities to the anchor's RT
  - Solve NNLS using only this m/z
- **Key constraint:** Only m/z values from anchor are processed; peaks in other spectra that don't exist in anchor are ignored

### Step 5: Restructure main demultiplex() loop
**File:** `StaggeredDemultiplexer.java` (lines 149-216)

**New structure:**
```java
for (int specIdx = 0; specIdx < cycleCenter.size(); specIdx++) {
    FragmentScan anchorSpectrum = cycleCenter.get(specIdx);
    float targetRT = anchorSpectrum.getScanStartTime();  // Anchor to actual RT

    DemuxWindow[] coveredSubWindows = findCoveredSubWindows(anchorSpectrum);

    for (DemuxWindow subWindow : coveredSubWindows) {
        // Skip edge subWindows if includeEdgeSubWindows=false
        if (!includeEdgeSubWindows && isEdgeSubWindow(subWindow)) {
            continue;
        }

        // Find covering spectra across all 5 cycles
        // Collect m/z from anchor spectrum ONLY (not other spectra)
        // Build interpolated intensity vector to anchorRT for each anchor m/z
        // Solve NNLS
        // Create output with anchor's RT, containing only anchor's m/z values
    }
}
```

### Step 6: Add edge detection helper
**File:** `StaggeredDemultiplexer.java`
```java
private boolean isEdgeSubWindow(DemuxWindow subWindow) {
    DemuxWindow[] all = designMatrix.getSubWindows();
    return subWindow.getIndex() == 0 || subWindow.getIndex() == all.length - 1;
}
```

### Step 7: Remove `calculateTargetRT()` averaging
- Delete or deprecate the method that averages cycleM1/cycleP1 times
- RT is now taken directly from each anchor spectrum

### Step 8: Update tests
**File:** `PwizValidationTest.java`
- Update output count expectations
- Verify demuxed spectrum RTs match their anchor spectrum RTs
- Test both includeEdgeSubWindows=true and false
- Update cosine similarity comparison logic

## Files to Modify

| File | Changes |
|------|---------|
| `core/src/main/java/org/searlelab/msrawjava/algorithms/StaggeredDemultiplexer.java` | Main restructure: new loop, new helpers, RT anchoring |
| `core/src/test/java/org/searlelab/msrawjava/algorithms/demux/PwizValidationTest.java` | Update output count expectations, RT validation |
| `core/src/test/java/org/searlelab/msrawjava/algorithms/StaggeredDemultiplexerTest.java` | Update basic tests for new output structure |

## Output Formula

- **Input:** N normal + M staggered spectra per cycle (N+M total)
- **SubWindows:** N+M+1 (from overlapping boundaries)
- **With edges (default):** 2*(N+M) demuxed spectra
  - Each of N+M spectra covers 2 subWindows → 2 outputs each
  - Edge subWindows (first/last) get 1 output from their single covering spectrum
- **Without edges:** 2*(N+M) - 2 demuxed spectra (excludes first and last subWindow outputs)

### Peak Content Per Output
- Each demuxed spectrum contains ONLY m/z values present in its anchor spectrum
- Peaks from other covering spectra are used for NNLS context but do NOT add new m/z to output

## Verification

1. **Unit tests:** Run `mvn test -Dtest="*Demux*,*demux*" -pl core`
2. **Output count with edges:** Verify demultiplex() returns 2*(N+M) scans (152 for 76-window input)
3. **Output count without edges:** Verify demultiplex(..., false) returns 2*(N+M)-2 scans (150 for 76-window)
4. **RT values:** Verify each demuxed spectrum's RT matches its anchor spectrum
5. **Peak content:** Verify each output only contains m/z values from its anchor spectrum
6. **Cosine similarity:** Re-run `testCosineSimilarityJavaVsPwiz` - expect improvement since RTs are now properly anchored
7. **Visual check:** Compare chromatogram extraction for test peptides

## Notes

- `RawFileConverters.java` line 175 already expects `2*cM1.size()` scan increment - no change needed
- The NNLS solving approach remains unchanged - only the iteration order and RT anchoring change
- pwiz reference: `OverlapDemultiplexer.cpp` anchors to `deconvSpectrum` RT (line 136)

---

# Investigation Notes: GTGIVSAPVPK Peak Dropout Issue (January 2026)

## Problem Statement
At RT=51.792 min, y2 (244.16561 Th) and y7 (697.42434 Th) peaks drop out completely (intensity=0) in Java demux output, while pwiz correctly retains them with full intensity (y2=4,234,870, y7=6,744,115). The original acquired spectrum at 51.792 min HAS these peaks.

## Test Case Details
- Peptide: GTGIVSAPVPK (z=2, precursor ~516.481 Th)
- Target sub-window: DemuxWindow[15: 512.48-520.48 Th]
- Target RT: 51.792 min
- Test file: `PwizValidationTest.testGTGIVSAPVPK_PeakDropout()`

## Key Observations
1. pwiz assigns the FULL original intensity to the demuxed output (4,234,870 for y2)
2. Java NNLS assigns ZERO to the same sub-window
3. The original spectrum at 51.792 min (isolation 512.49-528.49 Th) covers sub-windows 15 AND 16
4. The spectrum DOES contain y2 and y7 peaks

## Approaches Attempted and Results

### Approach 1: Collect m/z from ALL Covering Spectra (INCORRECT)
**Change:** Modified `collectMzValuesFromAnchor()` → `collectMzValuesFromCoveringSpectra()` to collect m/z values from all k-nearest spectra, not just the anchor.

**File:** `StaggeredDemultiplexer.java` line 148-150

**Result:** Peak dropout was NOT fixed. Cosine similarity metrics became MUCH WORSE:
- Before: Median ~0.88
- After: Median ~0.40

**Why incorrect:** This introduced peaks from adjacent but non-overlapping spectra as potential peaks in sub-windows they don't belong to. Fragment ions (like y2 at 244 Th) appear in MANY spectra regardless of isolation window, but attributing them to unrelated sub-windows corrupts the demultiplexing.

### Approach 2: Optimize Local Matrix to Only Include Covered Sub-Windows
**Change:** Modified `buildLocalMatrix()` to only include columns for sub-windows actually covered by selected spectra, removing all-zero columns.

**Result:** Did NOT fix the peak dropout. The issue is more fundamental than matrix sparsity.

### Approach 3: Fallback to Anchor Intensity When NNLS Returns Zero (INCORRECT)
**Change:** Added fallback logic: if NNLS returns 0 but the anchor has the peak, use anchor intensity divided by number of sub-windows anchor covers (typically 2).

**File:** `StaggeredDemultiplexer.java` lines 192-205

**Result:**
- Peak dropout "fixed" - peaks now appear at 50% of original intensity
- BUT this sweeps the real problem under the rug
- Overall cosine similarity metrics degraded significantly

**User assessment:** This is incorrect because it masks the underlying NNLS issue rather than addressing it.

## Root Cause Analysis (Partial)

### The Under-Determination Problem
The NNLS system for sub-window 15 with k=7 covering spectra typically has:
- Spectra with isolation 504-520 Th (covering sub-windows 14+15): row pattern [0,0,1,1,0,0,0]
- Spectra with isolation 512-528 Th (covering sub-windows 15+16): row pattern [0,0,0,1,1,0,0]

This creates a system where:
- x[14] + x[15] = intensity from 504-520 spectra
- x[15] + x[16] = intensity from 512-528 spectra

With 2 constraints and 3 unknowns (x[14], x[15], x[16]), the system is under-determined. NNLS can find valid solutions where x[15] = 0 by setting:
- x[14] = (intensity from 504-520 spectra)
- x[16] = (intensity from 512-528 spectra)
- x[15] = 0

This is mathematically valid but physically wrong for overlapping DIA windows.

### Questions for Further Investigation
1. **How does pwiz handle this?** pwiz assigns FULL original intensity - are they using NNLS at all, or a simpler approach?
2. **Is our NNLS implementation correct for this use case?** The Lawson-Hanson algorithm finds minimum-norm solutions, which may not be appropriate for DIA demultiplexing.
3. **Should we add regularization?** A prior that favors distributing intensity among overlapping sub-windows might help.
4. **Should we use a different solver?** Perhaps weighted least squares with center sub-window preference?

## Files Modified During Investigation
- `core/src/main/java/org/searlelab/msrawjava/algorithms/StaggeredDemultiplexer.java`
  - `collectMzValuesFromCoveringSpectra()` - collects from all covering spectra (INCORRECT)
  - `buildLocalMatrix()` - optimized to only include covered sub-windows
  - Fallback logic for anchor intensity (INCORRECT - masks the issue)

## Metrics Comparison
| Metric | Before Investigation | After Incorrect Fixes |
|--------|---------------------|----------------------|
| 5th percentile | ~0.70 | 0.11 |
| 25th percentile | ~0.85 | 0.26 |
| Median | ~0.88 | 0.40 |
| 75th percentile | ~0.90 | 0.58 |
| 95th percentile | ~0.95 | 0.88 |

## Next Steps (To Be Done Later)
0. Code is already reverted back to the original approach
1. User to assess NNLS implementation for this specific use case
2. Study pwiz OverlapDemultiplexer.cpp more closely to understand their approach
3. Consider alternative demultiplexing strategies (weighted averaging, Bayesian, etc.)
4. Possibly implement regularized NNLS that prefers non-zero overlapping sub-window contributions

### Example
I'm going to floor the window boundaries to make it easier to explain. If you are targeting the acquired
spectrum at RT=51.79236 from 512-528 as the anchor, the goal is to find the 512-520 component and the 520-528 component demultiplexed spectra, the
n=7 acquired windows are [512 to 528] and the surrounding three windows on either side ([504 to 520], [496 to 512], [488 to 504] below, and [520 to
536], [528 to 544], [536 to 552] above). We know exactly what the intensities for the [512 to 528] window at 51.79236 min because we acquired it,
but we have to interpolate what the peak intensities would look like for the neighboring spectra at rt=51.79236 min. We use these n=7 time aligned
spectra (Y) as well as the design matrix (X) to calculate what the sub-spectra (A) should be at rt=51.79236 min. We ignore the results of (A)
except for the 512-520 component and the 520-528 component because they are actually based on real data, instead of interpolated data, and then we
report those two components as the two demultiplexed spectra from the originally acquired 512-528 spectrum at rt=51.79236 min. When we go to the
next spectrum (presumably 528-544) then we have to redo all of the calculations because that spectrum was acquired at a new retention time. Please
let me know if you have any questions or want any clarifications about this algorithm and example.
