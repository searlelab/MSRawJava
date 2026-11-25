# Fast TIMS squashing across IMS — revised

**Canonical plan document:** `docs/uu-squash-across-ims.md`

## Intent

Add this public static method to `TIMSPeakPicker`:

```java
ArrayList<PeakWithIMS> squashAcrossIMS(ArrayList<PeakWithIMS> mzSortedPeaks)
```

Use deterministic, intensity-first greedy grouping in m/z, without IMS filtering or fitting. Report intensity-weighted m/z and IMS from half-max points. Keep existing callers unchanged.

Replace the proposed maximum tree with sorted primitive keys. Require focused regression tests, full-acquisition oracle verification, and representative performance measurements before considering implementation complete.

## Current understanding

- `TIMSPeakPicker.peakPickAcrossIMS` supplies the existing tolerance, greedy grouping conventions, consumption behavior, and output cap.
- `PeakWithIMS.compareTo` and `PeakIntensityComparator.compare` confirm that natural-sort indices reproduce intensity-comparator tie ordering among equal positive intensities.
- `APIAnnotationTest.assertAPI` currently hard-codes the historical introduction date. It needs a date-aware assertion for the new method.
- `API` permits independent class and method annotations. An isolated compile/Javadoc probe confirmed that an experimental method on a stable class is accepted and documented. The Maven configuration uses standard Javadoc without a custom lifecycle validator.
- Baseline compilation passed again. The previously run 15 picker replay and API tests passed. No tracked files have changed.

## Desired direction and scope

For each strongest remaining eligible point:

- Eligibility is exactly `p.isAvailable() && p.intensity > 0f`; NaN intensity is excluded.
- Center an inclusive window on the apex’s original m/z using `tolerance.getToleranceInMz(apex.mz, apex.mz)/2.0`.
- Consume every eligible point inside that window, regardless of IMS.
- Sum all consumed intensities in `double`, casting once to the output’s `float`.
- Compute intensity-weighted m/z and IMS using only points satisfying `p.intensity >= 0.5f*apex.intensity`. Evaluate that threshold in float; keep it and the window fixed throughout the group.
- Accumulate weighted products and their denominator in `double`, explicitly promoting intensity before multiplying by IMS.

Sort input and output using their existing natural order. Preserve initially unavailable and nonpositive points. Consume repeated references once; count distinct equal-valued objects separately.

Stop after producing `MAX_PEAKS` outputs. Points not consumed by those groups retain their availability. Omit the existing `totalIncluded` early exit deliberately: exhausting the candidate keys is sufficient and avoids list-entry versus object-identity counting.

## Implementation approach

1. **Record the plan and API contract.** At implementation start, write this plan verbatim to the canonical document. Add Javadoc covering exact comparisons, weighting, mutation, ordering, and cap behavior. Annotate the method `EXPERIMENTAL`, introduced in `v26.11.25`.
2. **Implement primitive candidate ordering.** Naturally sort the input, then pack each positive available candidate into a `long[]`:
   ```java
   ((long)Float.floatToIntBits(p.intensity) << 32) | (index & 0xffffffffL)
   ```
   Sort the populated range with `Arrays.sort` and traverse descending. Positive float encodings preserve intensity ordering; the index supplies deterministic ties. Recheck availability when visiting each candidate.
3. **Aggregate bounded windows directly.** Reuse the existing m/z bound helpers and scan each selected interval in ascending input order. Use object toggles as the sole consumption state. Allocate no per-group collections, median scratch, smoothing buffers, or boxed indices. Add no tree or additional indexing structure.
4. **Extend test infrastructure narrowly.** Keep a separate comparator-sort squash implementation as the test oracle, with independently written aggregation and boundary selection. Reuse fixture cloning that preserves aliases. Add an experimental-method assertion beside the existing picker assertion in `APIAnnotationTest`; extend its helper to accept an expected introduction date without weakening existing assertions.

Document sorting costs and bounded scans accurately. Treat small overlap counts and input-sort dominance as hypotheses to measure, rather than universal complexity or performance claims.

## Validation approach

**Focused automated coverage**

Add `TIMSSquashAcrossIMSTest` with:

- Analytical weighted-coordinate examples and a precision regression where float summation loses many small contributions.
- Exact half-max inclusion, adjacent float values, and subnormal threshold rounding.
- NaN, zero, negative, and initially unavailable intensity exclusion.
- Inclusive m/z boundaries and adjacent representable values; overlapping windows and fixed-apex grouping.
- Widely separated IMS values, ties, aliases, distinct equal objects, empty/singleton inputs, and shuffled-input determinism.
- More than `MAX_PEAKS` isolated groups, checking which points remain available.
- Fixed-seed randomized and adversarial dense/overlapping inputs compared with the oracle.
- Recorded MS1 and DIA/MS2 fixtures, comparing ordered output values and final input order/availability exactly.

Do not add the proposed concurrent-calls test. Keep existing picker goldens unchanged.

**Full-acquisition correctness gate**

Use the supplied `PIQ001_EVOSEP01_TIMS03_PRO_HT_BOB2_NP3_Pooled_Loading_Curve_100SPD_0065_S3-H2_1_644.d` acquisition through the existing reader.

Process every MS1 and MS2 spectrum in bounded retention-time batches, using conversion thresholds MS1=3 and MS2=1. Reconcile processed spectrum identities against `getScanSummaries`, accounting for inclusive batch boundaries and multiple MS2 windows per frame.

For each spectrum, run optimized and reference squash on independent fresh inputs and compare all ordered output values and final input availability exactly. Include empty spectra. Fail on any mismatch, missing spectrum, unexpected duplicate, or reader error.

Write a machine-readable report under `core/target/squash-across-ims-validation/` containing source fingerprints, coverage counts, point/output counts, cap hits, and comparison results. On failure, retain the spectrum identity and replayable input for diagnosis. This acquisition-dependent check remains an explicit manual validation command outside ordinary unit tests.

**Performance measurements**

Retain two-second windows starting at 10%, 30%, 50%, 70%, and 90% of the acquisition span. Also retain the five spectra with the highest eligible-point counts per MS level from the full-file pass.

Compare optimized squash, comparator-sort squash, and the existing picker using five fresh JVMs, each with 20 warmup and 30 measured workload repetitions. Exclude reading and cloning; include each method’s required sorting. Rotate method order across JVMs.

Report separately for MS1/MS2 and representative/dense workloads:

- Standalone natural input-sort time on its own line.
- Complete method time, allocations, and speed ratios.
- Input/output counts and bounded-scan visits per input point.

Require zero oracle mismatches. Report measured performance without a fixed speedup threshold or unit-test timing assertions.

**Repository checks**

```text
mvn -pl core -am -Dskip.build.natives=true -Dtest=TIMSSquashAcrossIMSTest,TIMSPeakpickerTest,PeakPickerReplayTest,APIAnnotationTest test
mvn -pl core,gui -am -Dskip.build.natives=true test
mvn -pl core,gui -am -Dskip.build.natives=true -Dmaven.test.skip=true compile
mvn -pl core -am -Dskip.build.natives=true -DskipTests javadoc:javadoc
```

Inspect generated Javadoc for the method, lifecycle annotation, and contract; the plugin’s `failOnError=false` means exit status alone is insufficient.

## Risks, decisions, and reviewer focus

Independent review should concentrate on packed-key ordering, promotion before weighted multiplication, exact cutoff/window comparisons, aliases, cap availability, and full-file coverage reconciliation.

The exact float threshold expression governs subnormal rounding; multiplication by `0.5f` is not universally exact for subnormals.

Performance findings may motivate a later proposal, but adding a tree requires profiling evidence and a plan revision. Existing picker behavior, conversion/GUI callers, dependencies, native code, and goldens remain unchanged. Do not stage, commit, or push.
