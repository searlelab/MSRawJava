# RawBrowserPanel (DIABrowserPanel clone) — Project Specification

## 1) Purpose & scope
Create a new GUI panel `org.searlelab.msrawjava.gui.visualization.RawBrowserPanel` that functionally mirrors EncyclopeDIA’s `DIABrowserPanel` (from `/Users/searle.brian/Documents/java/encyclopedia/src/main/java/edu/washington/gs/maccoss/encyclopedia/gui/dia/DIABrowserPanel.java`), reusing MSRawJava’s existing model + chart utilities wherever possible. The panel should let a user load a single raw input (`.d`, `.raw`, or `.dia`) and explore:

- Scan list with filter/search
- TIC + basepeak context
- Spectrum plot with optional IMS view
- Intensity distributions
- Ion injection time boxplots
- Window/structure visualization (local + global)

This spec defines classes, UI layout, data flow, and algorithms needed to implement the clone inside MSRawJava’s `gui/` and `core/` modules.

## 2) Source references (canonical behavior)
Primary behavior to clone from EncyclopeDIA:
- `DIABrowserPanel` (`.../gui/dia/DIABrowserPanel.java`)
- `DIAScanTableModel` (`.../gui/dia/DIAScanTableModel.java`)
- `MzmlStructureCharter` (`.../gui/dia/MzmlStructureCharter.java`)
- Chart utilities: `Charter` (`.../gui/general/Charter.java`)
- Progress + file UI: `SwingWorkerProgress`, `FileChooserPanel`, `LabeledComponent` (`.../gui/general/...`)
- Spectrum merging + IMS wrapper: `SpectrumUtils`, `SpectrumComparator`, `IMSSpectrumWrapper`
- Histogram utilities: `PivotTableGenerator`, `Log`
- (Optional) contaminant polymer traces: `ChromatogramExtractor`, `PolymerIon`, `TransitionRefiner`

MSRawJava equivalents to reuse:
- Model: `AcquiredSpectrum`, `PrecursorScan`, `FragmentScan`, `Range`, `MassTolerance` (`core/src/main/java/org/searlelab/msrawjava/model/...`)
- Readers: `BrukerTIMSFile`, `ThermoRawFile`, `EncyclopeDIAFile` (`core/src/main/java/org/searlelab/msrawjava/io/...`)
- Charts: `BasicChartGenerator`, `ExtendedChartPanel`, `XYTrace`, `XYTraceInterface`, `GraphType`, `AcquiredSpectrumWrapper` (`gui/src/main/java/org/searlelab/msrawjava/gui/charts/...`)
- Utilities: `MatrixMath`, `QuickMedian` (`core/src/main/java/org/searlelab/msrawjava/algorithms/...`)
- GUI threading examples: `RawFileBrowser`, `FileDetailsDialog`

## 3) Target package & new classes
**New package:** `gui/src/main/java/org/searlelab/msrawjava/gui/visualization`

### 3.1 Core classes
1) `RawBrowserPanel` (JPanel)
- Main UI panel; mirrors DIABrowserPanel layout and behavior.
- Public constructor should allow passing any configurable parameters (tolerance, scan limits) but can default to internal constants.
- Optional `main` or `launch` helper to open in a JFrame.

2) `RawScanTableModel` (AbstractTableModel)
- Clone of `DIAScanTableModel` with columns:
  - `#` (index)
  - `Spectrum Name`
  - `Scan Start Time (min)`
  - `Precursor m/z`
- Backs JTable and supports `updateEntries(List<AcquiredSpectrum>)`.

3) `RawBrowserInput` (only if needed)
- Only introduce if the constructor would exceed ~5 inputs.
- Otherwise prefer `RawBrowserPanel(StripeFileInterface stripe, String displayName)`.
- `RawBrowserPanel` should **not** open files or manage file choosers.

4) `RawSpectrumMergeUtils`
- Ports `SpectrumUtils.mergeSpectra` logic to MSRawJava types.
- Produces a merged `AcquiredSpectrum` (likely `PrecursorScan`) with combined m/z + intensity arrays.
- Supports ion mobility merging when present.
- Chooses between “accurate merge” and “binned merge” based on selection size (same cutoffs as EncyclopeDIA: >50 → binned).

5) `VisualizationCharts`
- Extends MSRawJava’s charting with **boxplot** and **shape** charts:
  - Boxplot: clone of `Charter.getBoxplotChart(...)`
  - Shape chart: clone of `Charter.getShapeChart(...)`
- Returns `ExtendedChartPanel` so it integrates with existing chart code.

6) `HistogramUtils`
- Port of `PivotTableGenerator` + `Log.protectedLog10` behavior for intensity histograms.
- Builds binned histogram as `XYTrace`.

7) `ImsSpectrumWrapper` (if needed)
- Wraps an `AcquiredSpectrum` but sets `GraphType.imsspectrum` to trigger IMS rendering in `BasicChartGenerator`.

## 4) UI layout (clone of DIABrowserPanel)
```
+--------------------------------------------------------------+
| Left: Options + Scan Table | Right: Tabs                      |
|-----------------------------|---------------------------------|
| [Search box]                | Tabs:                           |
| [Scan table]                | - Scans (default)               |
| [Scan table]                | - Intensity Distributions       |
|                             | - Range Statistics              |
|                             | - Structure (local)             |
|                             | - Global (overall)              |
+--------------------------------------------------------------+
```

### 4.1 Scans tab (split panes)
- **Top:** TIC chart (precursor TIC vs RT, area plot). Add marker overlay when a scan is selected.
- **Bottom left:** Spectrum plot (selected scan or merged selection). If IMS present, split into spectrum + IMS scatter.
- **Bottom right:** Log10 intensity histogram for selected scan.

### 4.2 Intensity Distributions tab
- **Top:** Precursor basepeak trace (no polymer contaminants for now).
- **Bottom:** Tabbed histogram views:
  - “Precursor Intensity”
  - “Fragment Intensity”

### 4.3 Range Statistics tab
- Boxplots of Ion Injection Time (ms):
  - by isolation window range
  - by RT bin (5 min bins)

### 4.4 Structure + Global tabs
- Structure view showing DIA window coverage vs RT (local snapshot).
- Global view showing each DIA window’s RT span across entire run.
- **Always render both tabs** and compute them with a full sweep (no optional/approximate mode).

## 5) Data flow & threading

### 5.1 File load
- The panel receives an **already-open** `StripeFileInterface` (no file chooser or opener).
- Preferred constructor: `RawBrowserPanel(StripeFileInterface stripe, String displayName)` unless more than ~5 inputs are required.
- Trigger background load via `SwingWorker` (do NOT block EDT).
- UI shows a modal progress dialog or non-modal `LoadingPanel` (reuse patterns from `FileDetailsDialog` or create a small `ProgressDialog`).
- On completion, update UI on EDT: table model, tabs, charts.

### 5.2 Close / Cancel / Replace load
- This dialog will be used in a model way so we do not need to worry about canceling or replacing the visualization.
- Close the provided `StripeFileInterface` on panel disposal/window close.

### 5.3 Table selection
- On selection change:
  - Single selection → render its spectrum.
  - Multi-selection → merge spectra and render a combined spectrum.
- Always update TIC marker to reflect selection RT range.

## 6) Algorithms & computations

### 6.1 Scan list
- `ArrayList<AcquiredSpectrum> scans = precursors + stripes`.
- Sort by scanStartTime (then spectrumIndex, then isolation window) similar to EncyclopeDIA’s `SpectrumComparator`.

### 6.2 TIC + basepeak
- Prefer `StripeFileInterface.getTICTrace()` for efficiency.
- Basepeak trace: compute from precursor scans (same as DIABrowserPanel).
- Keep `maxTIC` for selection marker height.

### 6.3 Log intensity histograms
- For each scan or scan collection:
  - Compute protected `log10(intensity)` (0 for non-positive).
  - Build histogram bins using PivotTable logic (bin count derived from N, clamp 50–200 as in EncyclopeDIA).

### 6.4 Ion injection time boxplots
- For each `FragmentScan`:
  - Use `getIonInjectionTime()` if non-null.
  - Group by isolation window `Range` → boxplot 1
  - Group by RT bin (5-minute bins) → boxplot 2

### 6.5 Structure charts
**Local Structure (scan snapshot):**
- Build from `StripeFileInterface.getRanges()` for now.
- Always present; do not depend on optional/approximate logic.

**Global Structure:**
- Build from `StripeFileInterface.getRanges()` for now.
- Always present and regenerated for each new input.

**Note:** We can revisit full-sweep spectrum traversal later if `getRanges()` is insufficient.

## 7) Configuration knobs (defaults)
- `MAX_SCANS_PER_TYPE` (default 500 as in `MzmlStructureCharter`)
- `MERGE_BIN_WIDTH` (0.1 m/z for binned merge)
- `RT_BIN_MINUTES` (5 min for IIT binning)
- `INTENSITY_HIST_BINS` (auto: 50–200)
- `LOG_EPS` (use protected log10)
-- `SPECTRUM_MERGE_TOLERANCE` (default: `PPMMassTolerance(10)` per guidance)

## 8) Error handling & UX
- If file is unsupported: show label “Unsupported file type”.
- If data load fails: show error message + log exception to `Logger`.
- Ensure all UI updates on EDT; background tasks on `SwingWorker` or `ProcessingThreadPool`.
- Always close file handles when done or on cancel.

## 9) Reuse vs new code
**Reuse (direct):**
- `BasicChartGenerator`, `XYTrace`, `GraphType`, `AcquiredSpectrumWrapper`
- `MatrixMath`, `QuickMedian`
- `StripeFileInterface` readers (`BrukerTIMSFile`, `ThermoRawFile`, `EncyclopeDIAFile`)

**New/ported:**
- Boxplot + shape chart utilities (ported from EncyclopeDIA `Charter`).
- Histogram + log utilities (ported from `PivotTableGenerator` + `Log`).
- Spectrum merge utility (ported from `SpectrumUtils`).
- IMS wrapper (ported from `IMSSpectrumWrapper`).
- No file chooser or file opener; `StripeFileInterface` is injected.

## 10) Integration options
- Replace the existing basic TIC chart in `FileDetailsDialog` with `RawBrowserPanel` (primary integration point). `FileDetailsDialog` should open the file and pass the already-open `StripeFileInterface` into the panel.
- Optional: provide a standalone launcher `RawBrowserMain` for direct use.

## 11) Testing & verification
- Run all current unit tests after edits to `core/` to ensure no breaking changes are made to `core/`.

- **Manual UI smoke test** with:
  - A Bruker `.d` file
  - A Thermo `.raw` file
  - A `.dia` file
- Verify:
  - File loads without blocking UI
  - Table populates + search filter works
  - TIC chart displays + RT marker updates on selection
  - Spectrum view updates, IMS scatter shows when IMS exists
  - Histograms populate (precursor + fragment)
  - Boxplots render with IIT data
  - Structure tabs appear and render

- Optional unit tests:
  - Histogram binning (small synthetic arrays)
  - Merge spectra correctness (known inputs)

---

If you confirm the above, I’ll proceed to implementation with minimal diffs in `gui/` (and small utility additions in `core/` only if absolutely necessary).
