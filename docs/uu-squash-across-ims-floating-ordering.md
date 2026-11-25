# Squash candidate ordering: floating-point follow-up

**Canonical plan document:** `docs/uu-squash-across-ims.md` (unchanged).

The user's subsequent direction prioritizes complete picker wall time, then CPU, then memory, and prefers ordinary floating-point ordering when at most 10% slower than packed keys.

## Decision and implementation

Replace packed keys with exact-intensity buckets. This revises only candidate ordering in the original plan; the public signature, scientific grouping, weighting, mutation, tie order, cap, and integration scope remain unchanged.

- After natural sorting, build an invocation-local `TFloatIntHashMap` of intensity to head index and an `int[]` linking members of each exact-intensity bucket. Reuse the existing Trove dependency; add no dependencies.
- Push positive available points in natural order, making each bucket descend by natural-order index. This preserves the existing intensity comparator's descending m/z and IMS ties, including aliases.
- Sort the distinct `float[]` intensity values with `Arrays.sort`, then traverse levels and their member indices descending. Keep object availability toggles as the sole consumed state. No intensity rounding or quantization is introduced.
- Use zero as the map's unused key and -1 as its unused index: eligibility excludes both signed zeros and NaN. Positive infinity remains eligible under the existing `> 0f` contract.
- Scratch is O(n + u), where u is the number of distinct eligible intensities. Candidate construction is expected O(n) with hashing and level sorting costs O(u log u); this is not a worst-case linear guarantee.

## Evidence and validation

Unprofiled trials on the retained real-data workloads showed an ordinary stable comparator sort roughly 35–50% slower than packed keys. Two fresh-JVM bucket trials instead reduced complete picker wall time by about 11–23% across representative/dense MS1/MS2, with lower CPU time and allocation. Trial evidence is under `core/target/squash-across-ims-ordering-trial/`.

Inspect a separate JFR recording for CPU/allocation hotspots. Keep profiled timings out of acceptance comparisons. Add regressions for adjacent distinct float intensity levels and mixed exponent ranges, then rerun the original focused tests, full acquisition correctness gate, repository tests, compile, Javadoc checks, and the five-JVM benchmark protocol against the selected implementation. Preserve the prior packed-key validation and partial benchmark artifacts separately.
