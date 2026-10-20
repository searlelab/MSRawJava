# Exact reader optimization, starting with timsTOF

**Canonical plan document:** `docs/uu-exact-reader-optimization.md`  
Implementation completed on October 20, 2026. The approved plan below is retained for context; decisions and verification are recorded in [the results summary](exact-reader-optimization-results.json).

**Intent**

Improve speed while preserving exactly the same ordered results, with memory reduction as a secondary goal. Work in the current checkout for up to eight hours, accommodating concurrent work on this computer.

Accept exact optimizations when speed or memory improves repeatably and the other remains neutral. Submit any demonstrated regression for human review while continuing independent work.

**Current understanding**

- `TIMSPeakPicker.peakPickAcrossIMS` sorts its input, changes point availability, repeatedly searches object-list mass coordinates, and allocates per-apex lists and smoothing arrays. `PeakWithIMS.mz` is final, supporting investigation of an invocation-local mass-coordinate view.
- `RawFileConverters.writeTimsInternal` performs an additional MS1 sort immediately before the picker sorts again.
- `StaggeredDemultiplexer.buildIntensityCache` consumes corresponding windows across five cycles. Its design also depends on acquired-window definitions and neighboring windows within those cycles. Production conversion assembles cycles across read sections.
- `NNLSSolver.solve` computes an unread initial `AtA`; the separate `AtA` in `solveLeastSquares` remains necessary.
- Baseline Java compilation and all 46 tests in the handoff’s focused set passed.
- The shared handoff file currently contains a JavaPot agent’s requested quiet block for October 20, 08:50–09:05 CDT. Reservations must be reread when implementation starts; this historical entry is not assumed to establish current availability.

**Desired direction and scope**

Prioritize timsTOF, then demux and adjacent Java hotspots. Preserve public APIs, dependencies, native payloads, numerical policies, and application integration.

Defer Linux memory diagnostics, remote campaigns, native/server changes, scan-summary caching, `NNLSCache` activation, and unrelated correctness repairs. Document separately any discovered behavior that appears incorrect.

**Implementation approach**

1. **Baseline, coordination, and fixtures — first 90 minutes.**

   Save the canonical plan and inspect current changes again. Freeze source, build configuration, JDK, dependencies, and native-resource identities. Use unchanged current-HEAD classes as the primary baseline; retain the campaign JAR/tag provenance separately.

   Read and append intent to `~/Downloads/agent_handoff.txt`, preserving existing entries. Include agent/repository identity, timestamps in CDT and UTC, expected resource use, and requested start/end times. Negotiate nonoverlapping quiet blocks through explicit acknowledgements or counterproposals. Silence is not agreement. Reread immediately before testing; append completion, release, cancellation, or overrun notices. Do not occupy another agent’s block with builds, profiles, or substantial fixture capture. Continue source inspection and other light work while waiting. Request later blocks only when the next experiment is ready.

   Freeze the existing mobility CSV and one complete native DIA mobility range, including ordered raw bits, scan/window identities, metadata, options, availability, and reference-alias relationships.

   **Every warmup and measured picker invocation gets independent point objects.** Clone from an immutable fixture description using identity-based mapping: duplicate references within one input must map to the same cloned object, while distinct equal-valued points remain distinct. Reconstruct original list order and initial availability explicitly. Never reuse consumed points or rely on shallow list copies. Test the fixture factory itself.

2. **timsTOF candidates — through approximately hour four.**

   Evaluate independently before combining passing changes:

   - Build a primitive mass-coordinate view after the existing stable sort, preserving direct index-to-point correspondence and raw `<`/`<=` comparisons.
   - Reuse invocation-owned per-apex lists and Gaussian scratch storage, processing only populated lengths. Preserve traversal, float accumulation, exact median input length, Gaussian arithmetic, and apex ties. Keep `QuickMedian` unchanged.
   - Remove the redundant MS1 caller sort after differential conversion verification.

   Keep scratch local to each invocation. Preserve point identity, availability mutation, stable ordering, thresholds, complete mobility context, and `MAX_PEAKS`. Exclude monotonic-bound preprocessing and replacement of mutable point objects.

3. **Demux and secondary investigation — approximately hours four to six.**

   Capture actual production demux inputs rather than reconstructing cycles by an approximate grouping rule. Preserve all five complete cycles, neighboring windows used by each block, acquired-window definitions, order, retention times, configuration, tolerance, and starting scan number. Retain upstream records and section-boundary context needed to reproduce production cycle assembly. Capture any additional context found to be consumed before choosing the excerpt.

   Maintain two distinct checks: captured interpolation/NNLS systems isolate numerical mechanisms; complete ordered spectrum comparisons validate context, selection, metadata, and assembly.

   First evaluate removal of only the unread initial NNLS `AtA`. Preserve the fallback and all remaining EJML arithmetic.

   Investigate at most two additional Java candidates, ranked by measured removable cost and risk. Initial candidates are exact-size `trimMasses` copying and indexed Thermo protobuf mass extraction. Read callers and prove ownership before prototyping; record other discoveries as deferred work.

4. **Qualification and candidate decisions — reserve the final two hours.**

   Validate each mechanism independently and then the accepted combination. Automatically retain candidates only after exactness and repeatable benefit-with-neutrality checks pass.

   For a speed or memory regression, send an asynchronous review request containing the concrete diff, exactness results, speed/memory measurements, uncertainty, and recommendation. Preserve the candidate as a reviewable patch outside the accepted implementation. Continue other candidates, tests, documentation, and quiet-block preparation without depending on that decision. An unanswered request remains pending.

   Preserve unrelated changes, review the final diff, and stop within eight elapsed hours. Do not stage, commit, push, or adopt the dependency in EncyclopeDIA.

**Validation approach**

- Differentially compare ordered raw numeric bits, metadata, sorted reference identity, aliasing, and availability flags against the unchanged baseline.
- Cover duplicate values and references, exact boundaries and adjacent values, signed zero/nonfinite inputs, unavailable points, empty/singleton inputs, peak limits, and large-to-small scratch reuse.
- Verify independent concurrent invocations and applicable repeated-open, chunk-boundary, cancellation, close, and failure behavior. Compare rebuilt MS1/MS2 spectra and complete demux spectra separately from kernel outputs.
- Benchmark in negotiated quiet blocks, alternating fresh baseline/candidate JVMs with identical warmup, invocation counts, and fixture reconstruction. Include candidate setup/mapping costs; report fixture reconstruction separately and include it equally in end-to-end replay measurements.
- Use one benchmark process at a time, one worker for kernels, and at most two for bounded parallel replay. Collect at least six paired runs per retained candidate. Record wall time, CPU time, allocation, GC, sampled heap occupancy/commit, RSS, and scratch high-water sizes separately. Contaminated or inconsistent measurements are inconclusive, not neutral.
- Run the handoff’s focused tests plus new regressions, then:
  - `mvn -pl core,gui -am -Dskip.build.natives=true test`
  - `mvn -pl core,gui -am -Dskip.build.natives=true -Dmaven.test.skip=true compile`

**Risks, decisions, and reviewer focus**

Primitive coordinates add storage; scratch reuse may reduce allocation while increasing temporary retention. Neither proves memory neutrality without evidence. Mutable fixture reset, alias preservation, stable ties, populated lengths, and full demux context are primary correctness concerns.

Unavailable native prerequisites or quiet blocks limit qualification rather than justify substituted evidence. Complete calibrated EncyclopeDIA windows and searches remain a separate integration gate. Report accepted, pending, rejected, and inconclusive candidates with reproducible commands and manifests; make no whole-search speedup or Linux native-memory claim from local Java experiments.
