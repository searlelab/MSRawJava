# STYLE NOTES

## Scope and exclusions
- Included: `core/src/main/java/**` and `gui/src/main/java/**`
- Excluded: `**/src/test/java/**`, `**/test/**`, `**/tests/**`, `**/generated/**`, `**/build/**`, `**/target/**`, and any files marked `@Generated` (none found)

## CORE house style summary
- Tabs for indentation; K&R brace style.
- Compact spacing around operators (e.g., `a=b`, `if (x==y)`), minimal whitespace.
- Imports grouped with blank line between JDK/third‑party/org when multiple groups are present.
- Class-level Javadocs are common for public API classes; method Javadocs used for key entry points.
- Public APIs often expose concrete types (`ArrayList`, `HashMap`) rather than interfaces.
- Heavy use of primitive arrays (`double[]`, `float[]`) and primitive math utilities.
- Null checks with early returns are common (`if (x==null) return ...`).
- Optional is used selectively (not consistently) for absent values.
- Comparators implemented via nested classes or inline lambdas; `Collections.sort` and `List.sort` both appear.
- Custom `Logger` is the dominant logging mechanism.
- Exceptions are typically propagated upward rather than wrapped in domain-specific result types.
- Immutability is implied by `final` fields but defensive copying is uncommon.

## GUI house style summary
- Tabs for indentation; K&R brace style.
- Compact spacing around operators with minimal extra whitespace.
- Imports grouped with blank line between JDK/`javax` and project imports.
- Most Swing classes define `serialVersionUID`.
- Class-level Javadocs are uncommon; comments are short and inline.
- UI construction is done in constructors or `init`/`build` helpers.
- Swing updates generally happen on EDT via `SwingWorker` or `SwingUtilities.invokeLater`.
- `GUIPreferences` is the canonical place for persistent UI settings.
- Logging typically goes through core `Logger`.
- Background work uses a mix of `SwingWorker`, custom pools, and ad-hoc `ExecutorService`s.

## Style shift report (CORE)

### Formatting
- **(1) Mixed use of `var` vs explicit types**
  - Severity: Low
  - Location: `core/src/main/java/org/searlelab/msrawjava/io/thermo/ThermoRawFile.java` (ThermoRawFile#openFile/getMetadata/getRunSummary) and `core/src/main/java/org/searlelab/msrawjava/io/thermo/GrpcServerLauncher.java` (GrpcServerLauncher#close)
  - Evidence:
    ```java
    var rep=stub.open(OpenRequest.newBuilder().setPath(rawFile.toAbsolutePath().toString()).build());
    var req=Session.newBuilder().setSessionId(sessionId).build();
    ...
    try (var walk=Files.walk(workDir)) {
    ```
  - Why it’s inconsistent/brittle: Elsewhere in CORE, explicit types are the norm; `var` appears only in Thermo classes, which makes the style feel uneven and can make debugging stack traces harder when types aren’t obvious.
  - Recommendation: Either adopt `var` as a core-wide convention (document it) or replace these with explicit types in Thermo classes for consistency.
  - Preferred pattern:
    ```java
    OpenReply rep=stub.open(OpenRequest.newBuilder().setPath(rawFile.toAbsolutePath().toString()).build());
    Session req=Session.newBuilder().setSessionId(sessionId).build();
    try (java.util.stream.Stream<Path> walk=Files.walk(workDir)) {
    ```

- **(2) Diamond operator vs explicit generic constructors**
  - Severity: Low
  - Location: `core/src/main/java/org/searlelab/msrawjava/io/tims/TIMSPeakPicker.java` (explicit generics) vs `core/src/main/java/org/searlelab/msrawjava/algorithms/StaggeredDemultiplexer.java` (diamond)
  - Evidence:
    ```java
    ArrayList<PeakWithIMS> intensitySortedPeaks=new ArrayList<PeakWithIMS>(mzSortedPeaks);
    ArrayList<ArrayList<PeakWithIMS>> finalPeaks=new ArrayList<ArrayList<PeakWithIMS>>();
    ```
    ```java
    ArrayList<FragmentScan> demuxResults=new ArrayList<>();
    ```
  - Why it’s inconsistent/brittle: The module otherwise uses a single style per class; mixing old and new generic syntax in adjacent core algorithms suggests uneven edits and complicates formatting expectations.
  - Recommendation: Normalize to diamond where possible (Java 7+) or keep explicit generics consistently within the module.

### Naming
- **(3) Mixed acronym casing for TIMS classes**
  - Severity: Medium
  - Location: `core/src/main/java/org/searlelab/msrawjava/io/tims/TimsReader.java` vs `core/src/main/java/org/searlelab/msrawjava/io/tims/TIMSPeakPicker.java` and `TIMSMassTolerance`
  - Evidence:
    ```java
    public final class TimsReader implements AutoCloseable {
    ```
    ```java
    public class TIMSPeakPicker {
    ```
  - Why it’s inconsistent/brittle: The same subsystem is referenced with two different acronym casings (Tims vs TIMS), which makes search/auto-complete inconsistent and suggests divergent naming standards.
  - Recommendation: Pick one acronym casing for the `io/tims` package and apply it consistently (e.g., rename `TimsReader` to `TIMSReader` or adjust the TIMS* classes to `Tims*`).

- **(4) Precursor m/z getter naming drift (`MZ` vs `Mz`)**
  - Severity: Medium
  - Location: `core/src/main/java/org/searlelab/msrawjava/model/AcquiredSpectrum.java` vs `core/src/main/java/org/searlelab/msrawjava/model/ScanSummary.java`
  - Evidence:
    ```java
    public double getPrecursorMZ();
    ```
    ```java
    public double getPrecursorMz() {
    ```
  - Why it’s inconsistent/brittle: The model uses two spellings for the same concept. This forces callers to remember multiple method names and suggests uneven API evolution.
  - Recommendation: Standardize on one naming convention (prefer `Mz` if that’s dominant elsewhere, or `MZ` if aligning with file formats) and provide deprecated bridges if necessary.

### Structure
- **(5) Missing `@Override` and mutable comparator constant**
  - Severity: Low
  - Location: `core/src/main/java/org/searlelab/msrawjava/model/Range.java` (Range#compareTo, RANGE_CONTAINS_COMPARATOR)
  - Evidence:
    ```java
    public int compareTo(Range o) {
    ...
    public static Comparator<Range> RANGE_CONTAINS_COMPARATOR=new Comparator<Range>() {
    ```
  - Why it’s inconsistent/brittle: Most Comparable implementations in core use `@Override`, and static comparators are commonly declared `final`. Missing annotations and mutable static comparators make refactors less safe.
  - Recommendation: Add `@Override` to `compareTo` and mark the comparator `public static final` for consistency.

- **(6) Inconsistent encapsulation of peak value objects**
  - Severity: Medium
  - Location: `core/src/main/java/org/searlelab/msrawjava/model/Peak.java` vs `core/src/main/java/org/searlelab/msrawjava/model/PeakWithIMS.java` and `PeakInTime`
  - Evidence:
    ```java
    private final double mz;
    private final float intensity;
    ```
    ```java
    public final double mz;
    public final float intensity;
    public final float ims;
    ```
  - Why it’s inconsistent/brittle: Some peak types expose public fields while others encapsulate them behind getters. This creates mixed usage patterns and discourages consistent defensive access.
  - Recommendation: Pick one style for peak value objects (prefer private final + getters) and align the others. If direct field access is desired for performance, document that explicitly and use it consistently across peak types.

### Documentation
- **(7) Class-level Javadoc is inconsistent across core utilities**
  - Severity: Low
  - Location: `core/src/main/java/org/searlelab/msrawjava/algorithms/MatrixMath.java`, `core/src/main/java/org/searlelab/msrawjava/threading/ProcessingThreadPool.java`, plus other utility/model classes without class Javadocs
  - Evidence:
    ```java
    public class MatrixMath {
    ```
  - Why it’s inconsistent/brittle: The majority of core classes include a class-level Javadoc, but several utility classes do not. This creates a noticeable documentation “style shift” and makes public utilities feel less polished.
  - Recommendation: Add brief class-level Javadocs to the remaining public core utilities, following the style used elsewhere.

### Error handling/logging
- **(8) Direct `System.out`/`System.err` output in core logic**
  - Severity: Medium
  - Location: `core/src/main/java/org/searlelab/msrawjava/algorithms/MatrixMath.java` (MatrixMath#print), `core/src/main/java/org/searlelab/msrawjava/io/tims/BrukerTIMSFile.java` (BrukerTIMSFile#<init>), `core/src/main/java/org/searlelab/msrawjava/algorithms/StaggeredDemultiplexer.java` (profile timing)
  - Evidence:
    ```java
    System.out.print("[");
    System.err.println("No MS1s found!");
    System.out.printf("Demux timing: ...");
    ```
  - Why it’s inconsistent/brittle: The module already centralizes output through `Logger`; direct stdout/stderr makes logging inconsistent and can bypass user-configured log routing.
  - Recommendation: Route informational/error output through `Logger` (or gate debug prints behind a dedicated Logger flag). If `System.out` is intentional for debug, keep it isolated behind a single debug utility.

### API/design patterns
- **(9) Optional ion mobility vs direct array indexing**
  - Severity: High
  - Location: `core/src/main/java/org/searlelab/msrawjava/model/PrecursorScan.java` (PrecursorScan#getPeaks), `core/src/main/java/org/searlelab/msrawjava/model/FragmentScan.java` (FragmentScan#rebuild/getPeaks)
  - Evidence:
    ```java
    // FragmentScan.rebuild
    if (!anyIMS) {
        newIonMobilityArray=null;
    }
    ...
    // PrecursorScan.getPeaks / FragmentScan.getPeaks
    peaks.add(new PeakWithIMS(massArray[i], intensityArray[i], ionMobilityArray[i]));
    ```
  - Why it’s inconsistent/brittle: The interface advertises optional ion mobility (`Optional<float[]>`), but peak extraction assumes a non-null array. This contradicts the model contract and will NPE when IMS is absent.
  - Recommendation: Guard against null IMS arrays in `getPeaks` (e.g., use a `Peak` or `PeakInTime` variant when IMS is absent, or substitute a sentinel). Align the model API so optionality is handled consistently.
  - Preferred pattern:
    ```java
    float ims=(ionMobilityArray!=null&&ionMobilityArray.length>i)?ionMobilityArray[i]:Float.NaN;
    peaks.add(new PeakWithIMS(massArray[i], intensityArray[i], ims));
    ```

- **(10) Interface contract mismatch for peak toggling**
  - Severity: Medium
  - Location: `core/src/main/java/org/searlelab/msrawjava/model/PeakInterface.java` vs `core/src/main/java/org/searlelab/msrawjava/model/Peak.java`
  - Evidence:
    ```java
    void turnOff();
    void turnOn();
    ```
    ```java
    public void turnOff() {
        throw new UnsupportedOperationException("turnOff not implemented");
    }
    ```
  - Why it’s inconsistent/brittle: The interface implies all peak types are toggleable, but `Peak` throws at runtime. This encourages fragile callers and mixed expectations.
  - Recommendation: Either remove toggling from `PeakInterface` (or split into a sub-interface), or implement a no-op toggle consistently across implementations.

- **(11) Concrete collection types in public API signatures**
  - Severity: Low
  - Location: `core/src/main/java/org/searlelab/msrawjava/io/OutputSpectrumFile.java`
  - Evidence:
    ```java
    void setRanges(HashMap<Range, WindowData> ranges);
    void addSpectra(ArrayList<PrecursorScan> precursors, ArrayList<FragmentScan> stripes);
    ```
  - Why it’s inconsistent/brittle: Most APIs accept `Map`/`List` interfaces, but these methods require concrete implementations. This forces callers into specific types and diverges from common Java API conventions.
  - Recommendation: Use interface types (`Map`, `List`) in public method signatures unless mutation semantics require a concrete type.

## Style shift report (GUI)

### Formatting
- **(12) Local `var` usage is isolated to one file**
  - Severity: Low
  - Location: `gui/src/main/java/org/searlelab/msrawjava/gui/visualization/RawBrowserDataLoader.java` (RawBrowserDataLoader#build)
  - Evidence:
    ```java
    var structure=StructureChartBuilder.buildLocalStructureChart(...);
    var global=StructureChartBuilder.buildGlobalStructureChart(...);
    ```
  - Why it’s inconsistent/brittle: GUI code otherwise uses explicit types. Isolated `var` usage implies an inconsistent style and complicates readability for contributors who expect explicit types.
  - Recommendation: Prefer explicit types in GUI, or adopt `var` consistently with a documented guideline.

- **(13) Fully qualified `SwingWorker` usage in one class**
  - Severity: Low
  - Location: `gui/src/main/java/org/searlelab/msrawjava/gui/visualization/RawBrowserPanel.java` (RawBrowserPanel#startLoad/updateToSelected)
  - Evidence:
    ```java
    new javax.swing.SwingWorker<RawBrowserData, Void>() {
    ```
  - Why it’s inconsistent/brittle: Other GUI classes import `SwingWorker` and use it unqualified. This single-file deviation is a style shift that complicates consistency.
  - Recommendation: Import `SwingWorker` and use the unqualified type like the rest of the module.

### Naming
- **(14) Mixed IMS acronym casing**
  - Severity: Low
  - Location: `gui/src/main/java/org/searlelab/msrawjava/gui/visualization/RawBrowserPanel.java` (field `peakPickAcrossIMS`) vs `gui/src/main/java/org/searlelab/msrawjava/gui/visualization/ImsSpectrumWrapper.java`
  - Evidence:
    ```java
    private final boolean peakPickAcrossIMS;
    ```
    ```java
    public class ImsSpectrumWrapper extends AcquiredSpectrumWrapper {
    ```
  - Why it’s inconsistent/brittle: Within GUI, both `IMS` and `Ims` are used for the same acronym. This creates naming drift and inconsistent symbol search.
  - Recommendation: Standardize on one acronym casing in GUI (e.g., `IMS` or `Ims`) and align field/class names accordingly.

### Structure
- **(15) Multiple concurrency patterns without a module default**
  - Severity: Medium
  - Location: `gui/src/main/java/org/searlelab/msrawjava/gui/filebrowser/DirectorySummaryPanel.java` (local fixed thread pool), `gui/src/main/java/org/searlelab/msrawjava/gui/ConversionPane.java` (cached thread pool + ProcessingThreadPool), `gui/src/main/java/org/searlelab/msrawjava/gui/RawFileBrowser.java` (SwingWorker)
  - Evidence:
    ```java
    private final ExecutorService pool=Executors.newFixedThreadPool(...);
    ```
    ```java
    private final ExecutorService executor=Executors.newCachedThreadPool();
    private final ProcessingThreadPool pool;
    ```
  - Why it’s inconsistent/brittle: The GUI uses three different concurrency patterns, which complicates lifecycle management and increases the risk of thread leaks or inconsistent performance tuning.
  - Recommendation: Pick a dominant GUI background pattern (e.g., `SwingWorker` for UI-bound tasks and `ProcessingThreadPool` for compute) and align the remaining cases, or centralize thread creation in a GUI-specific executor.

### Documentation
- **(16) Isolated class-level Javadocs in an otherwise undocumented module**
  - Severity: Low
  - Location: `gui/src/main/java/org/searlelab/msrawjava/gui/ConversionPane.java`, `gui/src/main/java/org/searlelab/msrawjava/gui/filebrowser/DirectorySummaryPanel.java`, `gui/src/main/java/org/searlelab/msrawjava/gui/loadingpanels/*`
  - Evidence:
    ```java
    /** Owns the conversion parameter bar, queue, dispatcher and details console. */
    ```
  - Why it’s inconsistent/brittle: GUI overall avoids class-level Javadocs, but a few classes have them, suggesting drift. This makes it unclear whether GUI classes are expected to be documented.
  - Recommendation: Either add short Javadocs across GUI public classes or remove the few class-level Javadocs to match the prevailing GUI style.

### Error handling/logging
- **(17) Direct `System.err` usage in GUI**
  - Severity: Low
  - Location: `gui/src/main/java/org/searlelab/msrawjava/gui/filebrowser/DirectorySummaryPanel.java` (DirRow#fromBruker)
  - Evidence:
    ```java
    System.err.println("Error getting size of file "+f+": "+e.getMessage());
    ```
  - Why it’s inconsistent/brittle: GUI logging otherwise uses `Logger`. Direct stderr output bypasses log routing and verbosity controls.
  - Recommendation: Replace with `Logger.errorLine` or `Logger.errorException` for consistent GUI logging.

- **(18) Silent exception swallowing in lifecycle hooks**
  - Severity: Medium
  - Location: `gui/src/main/java/org/searlelab/msrawjava/gui/GuiMain.java` (shutdown hook and window closing)
  - Evidence:
    ```java
    } catch (Throwable ignore) {
    }
    ```
  - Why it’s inconsistent/brittle: Most GUI code logs exceptions; swallowing here makes shutdown failures invisible and is inconsistent with the rest of the module.
  - Recommendation: Log at least a brief message or exception via `Logger` before suppressing, or narrow the catch to known exceptions.

### API/design patterns
- **(19) Multiple optional-value strategies in GUI data flow**
  - Severity: Low
  - Location: `gui/src/main/java/org/searlelab/msrawjava/gui/visualization/RawBrowserDataLoader.java` (null checks), `gui/src/main/java/org/searlelab/msrawjava/gui/visualization/RawBrowserPanel.java` (Optional usage)
  - Evidence:
    ```java
    if (iit!=null&&iit>0) list.add(iit*1000f);
    ...
    boolean hasIms=displaySpectrum.getIonMobilityArray().isPresent();
    ```
  - Why it’s inconsistent/brittle: GUI mixes null checks with Optional-based access for the same conceptual “optional” fields, which can lead to inconsistent handling paths.
  - Recommendation: Prefer a single optional-value strategy in GUI (match core’s Optional usage where available) to keep data handling consistent.

## Top consistency fixes
1) **Core:** Align IMS optional handling by guarding `ionMobilityArray` access in `PrecursorScan#getPeaks` and `FragmentScan#getPeaks` (prevents NPEs and matches Optional contract).
2) **Core:** Standardize TIMS acronym casing across `io/tims` (rename `TimsReader`/`TimsNative` or align `TIMS*` classes).
3) **Core:** Unify precursor m/z naming (`getPrecursorMZ` vs `getPrecursorMz`) and deprecate the nonstandard form.
4) **Core:** Route core debug output through `Logger` and remove direct `System.out/err` in algorithm/IO classes.
5) **Core:** Make peak value objects consistent (either all public fields or all private + getters) and clarify toggle behavior in `PeakInterface`.
6) **GUI:** Set a single GUI background-execution pattern (SwingWorker for UI tasks + a shared executor for long IO) and replace ad-hoc executors.
7) **GUI:** Replace `System.err` usage in `DirectorySummaryPanel` with `Logger` and log shutdown failures in `GuiMain`.
8) **GUI:** Standardize IMS acronym casing (`IMS` vs `Ims`) across visualization classes and preferences.
9) **GUI:** Remove or spread class-level Javadocs to make GUI documentation style consistent.
10) **Core/GUI:** Decide on `var` usage (either allow in both modules or remove from the few isolated usages).
