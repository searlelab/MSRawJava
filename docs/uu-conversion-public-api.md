# Derisk Conversion Public APIs

**Canonical plan document:** `docs/uu-conversion-public-api.md`

## Intent

Provide a library-oriented, single-input conversion API that owns reader selection, demultiplexing decisions, worker-pool lifecycle, output-path resolution, and cleanup. Separate per-conversion settings from CLI discovery, presentation flags, and batch orchestration while preserving existing callers during migration.

## Current understanding

- `Main.convertKnownFiles(ConversionParameters)` currently combines discovery, batch processing, logging behavior, reader setup, demux selection, output naming, and `ProcessingThreadPool` lifecycle.
- Its early empty-input return and exceptional paths bypass `pool.close()`.
- `RawFileConverters.writeStandard`, `writeDemux`, and `writeTims` return cancellation/success booleans, but `Main` discards them.
- `ConversionParameters.Builder` exposes writer settings alongside file lists, discovery flags, thread limits, logging, and CLI presentation flags.
- Equivalent demux and output-path logic is duplicated in `Main` and `gui/ConversionPane`.

## Desired direction and scope

- Add an immutable `ConversionOptions` type for output format, intensity thresholds, demultiplexing mode, precursor margin, demux tolerance, and `DemuxConfig`.
- Construct options through a top-level `ConversionOptionsBuilder`; do not expose another nested builder.
- Add `ConversionRequest` for one input path, optional output directory or explicit output path, optional worker-thread limit, options, and progress indicator.
- Add `ConversionResult` containing the resolved output `Path` and `ConversionStatus` (`COMPLETED` or `CANCELED`). Operational failures continue to throw their original exception after cleanup rather than being reduced to a status.
- Add a `RawFileConversion` facade with `convert(ConversionRequest)` that:
  - creates and closes `ProcessingThreadPool` with try-with-resources;
  - identifies and opens the supported input reader;
  - applies automatic/forced demultiplexing and precursor-margin validation;
  - selects standard, demultiplexed, or Bruker conversion;
  - resolves collision-safe and `.demux` output names before invoking the writer;
  - returns the writer boolean as `ConversionStatus`;
  - closes readers, writers, progress resources owned by the facade, Thermo resources, and the pool on completion, cancellation, failure, and invalid/empty input.
- Reject a null, missing, unreadable, or unsupported single input with a clear argument/input exception before conversion. An empty CLI discovery result remains a nonfatal CLI condition.
- Move shared demux resolution and output naming out of `Main`/`ConversionPane` into package-level conversion helpers used by the facade, CLI, and GUI.
- Retain `ConversionParameters.builder()` and `Main.convertKnownFiles(...)` as deprecated compatibility APIs. Migrate repository code and documentation to the new types; compatibility methods delegate through the new facade without changing CLI behavior.
- Keep CLI-only state—file discovery, log path, batch/silent/ANSI flags, and batch orchestration—in `Main.CliArguments` or a package-private CLI request type, not in `ConversionOptions`.
- Do not change low-level `RawFileConverters` signatures, native/server protocols, dependencies, or output contents.

## Implementation approach

1. Write this settled plan verbatim to `docs/uu-conversion-public-api.md`.
2. Introduce the options, request, result/status, and facade APIs in `core` with Java 11-compatible immutable classes and null/argument validation.
3. Centralize input opening, demux resolution, output naming, and resource ownership in the facade; ensure the resolved path supplied to the writer is exactly the path returned.
4. Refactor CLI batch conversion to discover inputs and call the facade once per file, collecting statuses while preserving current messages, unsupported-TSF handling, naming, and Thermo shutdown behavior.
5. Refactor GUI conversion jobs to use the shared resolution behavior while retaining the GUI-owned long-lived pool; expose an internal facade execution path that accepts an existing pool only package-privately, so the public library method always owns its pool.
6. Deprecate and delegate legacy surfaces, migrate internal tests/callers and the manual’s conversion example to the facade, and remove duplicated GUI/Main helper logic.

## Validation approach

- Add focused facade tests for standard conversion, automatic and forced demux, Bruker demux fallback, explicit output override, same-format collision naming, and exact returned output path/status.
- Add deterministic lifecycle tests using injectable package-private factories/test doubles to prove pool, reader, indicator, writer, and Thermo cleanup on success, thrown failure, cancellation, unsupported input, and empty legacy batch discovery.
- Verify legacy CLI parsing/defaults and `Main.convertKnownFiles` compatibility tests still pass.
- Run focused core tests, then `mvn -pl core,gui -am -Dskip.build.natives=true test`.
- Run the required Java compile: `mvn -pl core,gui -am -Dskip.build.natives=true -Dmaven.test.skip=true compile`.
- Re-read the complete diff for accidental API, output-format, GUI-threading, native, or unrelated changes.

## Risks, decisions, and reviewer focus

- Default assumption: preserve source compatibility through deprecated delegates; immediate removal would require an explicitly breaking release.
- `CANCELED` represents the existing writer `false` result; exceptions remain failures and are rethrown after deterministic cleanup.
- The public facade always owns its pool. Any existing-pool seam needed by the GUI remains non-public to prevent consumers from inheriting lifecycle responsibilities.
- Reviewer focus should be on double-close safety, interruption preservation when pool shutdown is interrupted, exact output-path agreement, Thermo server cleanup across batches, and avoiding behavior drift between CLI and GUI.
