# Thermo section spectrum deduplication

**Canonical plan document:** `docs/uu-thermo-section-spectrum-dedup.md`

## Intent

Ensure sectioned Java conversion emits each logical source spectrum at most once when a reader returns overlapping retention-time results, while preserving source order, cancellation behavior, spectrum contents, output schemas, native protocols, and existing CLI behavior.

## Current understanding

`RawFileConverters.writeStandard` and `writeDemux` query 100 adjacent retention-time sections. Their `Math.nextDown` upper-bound adjustment prevents overlap from exactly inclusive boundaries, but the Thermo server expands both ends of every request by `1e-6` minutes, so the same Thermo scan number can still appear in consecutive results. `ThermoRawSpectrumReader` uses that scan number as the stable `spectrumIndex`, and EncyclopeDIA stores MS1 and MS2 indices in separate primary-keyed tables. A converter-level invariant is therefore needed in addition to non-overlapping nominal query bounds.

## Desired direction and scope

Keep the fix in `core` at the sectioned conversion boundary. Track emitted source indices independently for precursor and fragment spectra, retain only each level's first occurrence, and apply the same filtering before standard writes and before demux cycle assembly/MS1 writes. Separate per-level identity preserves legitimate MS1 and MS2 records that share a numeric index. Do not change section progression, the Thermo server, protobufs, writers, output schemas, spectrum models or arrays, demultiplexing algorithms, cancellation checks, or unrelated conversion paths.

## Implementation approach

1. Create this canonical plan document before implementation changes and keep it unchanged during the work loop.
2. In `RawFileConverters`, add a small order-preserving helper based on the model's stable `spectrumIndex`, create per-level seen-index sets for each conversion, and filter section results immediately after each reader call in `writeStandard` and `writeDemux`.
3. In `RawFileConvertersStandardTest`, make the fake reader optionally model inclusive retention-time tolerance and strengthen the boundary regression to prove tolerance-overlapped MS1 and MS2 spectra are written once while distinct records remain ordered.
4. Add an opt-in Thermo integration regression around the supplied large RAW file if it can be expressed without making the repository depend on that external fixture; assert conversion succeeds and the known boundary scan is present once in EncyclopeDIA output.

## Validation approach

Run the focused `RawFileConvertersStandardTest`, then compile `core` and `gui` with native rebuilds and tests skipped as required by repository guidance. If the supplied RAW and bundled Thermo server are available, run the opt-in integration regression or an equivalent direct conversion and inspect the resulting SQLite row for scan `42245`. Re-read the final diff and repository status for unintended changes.

## Risks, decisions, and reviewer focus

The key decision is that source identity is `(MS level, spectrumIndex)`: the model documents the index as stable, and EncyclopeDIA enforces uniqueness independently in its precursor and spectra tables. Reviewers should confirm no supported reader intentionally emits multiple logical records of the same MS level with one stable index. Filtering must occur before TIC accumulation, demux cycle assembly, progress counts, and asynchronous writer submission so all downstream behavior observes the single-emission stream. The external 1.3 GB RAW must remain optional and must not be copied into repository fixtures.
